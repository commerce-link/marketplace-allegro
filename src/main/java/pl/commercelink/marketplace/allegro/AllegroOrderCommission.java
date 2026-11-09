package pl.commercelink.marketplace.allegro;

import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The commission Allegro charged for one order, read from the seller's billing account. The order itself
 * ({@code checkout-forms}) carries no commission; billing does, as entries linked to the order by {@code order.id}.
 * <p>
 * Only commission counts: delivery, listing, promotion fees, rebates and account corrections are left out, as the
 * order's fee is the marketplace's commission on the sale (the same thing Empik and Morele report). It is net of VAT,
 * as the app counts a payment's fee as a net cost; billing books gross amounts with the VAT rate beside them. The
 * commission is read once, when the order is imported; anything going wrong yields zero so
 * the order is still imported.
 * <p>
 * One instance serves one fetch of orders. Billing is asked once per order, one after another, and the fetch runs
 * inside a queue message with a visibility timeout, so billing gets a time budget for the whole fetch: once it is used
 * up (a hanging or slow billing), the remaining orders get zero without asking.
 */
class AllegroOrderCommission {

    private static final System.Logger LOGGER = System.getLogger(AllegroOrderCommission.class.getName());
    private static final String ENDPOINT = "/billing/billing-entries";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    /**
     * Billing time for one fetch. The orders-import message is visible again after 300 s; one more call that hangs
     * until the HTTP client gives up (60 s) still ends the fetch well within it.
     */
    static final Duration BILLING_BUDGET = Duration.ofSeconds(120);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /**
     * Billing types that are a commission on the sale (ids from {@code GET /billing/billing-types}): sales commission,
     * on featured offers, on additional services, campaign sales and participation commissions, the unit transaction
     * fee and the alcohol category transaction fee.
     */
    static final Set<String> COMMISSION_TYPES = Set.of(
            "SUC", "FSF", "ASC", "BRG", "BC1", "BC2", "BC3", "BC4", "BC5", "USF", "KAC");

    private final RestApiWithRetry restApi;
    private final Clock clock;
    private final Instant deadline;

    AllegroOrderCommission(RestApiWithRetry restApi) {
        this(restApi, Clock.systemUTC());
    }

    AllegroOrderCommission(RestApiWithRetry restApi, Clock clock) {
        this.restApi = restApi;
        this.clock = clock;
        this.deadline = clock.instant().plus(BILLING_BUDGET);
    }

    /**
     * The commission of each line, in the order and number of {@code lineItems}. An offer's commission goes to the
     * first line of that offer; commission tied to no offer, or to an offer not on the order, goes to the first line.
     */
    List<BigDecimal> forLines(String checkoutFormId, List<AllegroCheckoutForm.LineItem> lineItems) {
        List<BigDecimal> perLine = new ArrayList<>(Collections.nCopies(lineItems.size(), ZERO));
        if (lineItems.isEmpty()) {
            return perLine;
        }
        List<AllegroBillingEntriesResponse.Entry> entries;
        try {
            entries = commissionEntries(checkoutFormId);
            for (AllegroBillingEntriesResponse.Entry entry : entries) {
                // a charge is negative, so its commission is the negated amount; a refund (positive) reduces it
                BigDecimal commission = net(entry).negate();
                int line = lineOf(entry, lineItems);
                perLine.set(line, perLine.get(line).add(commission));
            }
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Allegro billing unavailable for checkout form {0}: {1}",
                    checkoutFormId, describe(e));
            return new ArrayList<>(Collections.nCopies(lineItems.size(), ZERO));
        }
        if (entries.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "No Allegro commission yet for checkout form {0}", checkoutFormId);
        }
        if (perLine.stream().anyMatch(value -> value.signum() < 0)) {
            // a refund on another line than its charge: the order's total counts, never below zero, on the first line
            BigDecimal total = perLine.stream().reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO);
            List<BigDecimal> clamped = new ArrayList<>(Collections.nCopies(lineItems.size(), ZERO));
            clamped.set(0, total.setScale(2, RoundingMode.HALF_UP));
            return clamped;
        }
        return perLine.stream().map(value -> value.setScale(2, RoundingMode.HALF_UP)).toList();
    }

    private List<AllegroBillingEntriesResponse.Entry> commissionEntries(String checkoutFormId) {
        List<AllegroBillingEntriesResponse.Entry> commission = new ArrayList<>();
        int offset = 0;
        int pages = 0;
        List<AllegroBillingEntriesResponse.Entry> page;
        do {
            if (pages++ == MAX_PAGES) {
                // an order has a handful of entries; endless full pages mean the endpoint ignores the offset, and
                // the same entries counted again would overstate the commission
                throw new IllegalStateException("still full after " + MAX_PAGES + " pages");
            }
            if (clock.instant().isAfter(deadline)) {
                throw new IllegalStateException("billing time budget of " + BILLING_BUDGET.toSeconds()
                        + " s for this fetch used up, not asked");
            }
            AllegroBillingEntriesResponse response = restApi.fetchWithAuthRetry(ENDPOINT,
                    Map.of("order.id", checkoutFormId, "limit", String.valueOf(PAGE_SIZE), "offset", String.valueOf(offset)),
                    AllegroBillingEntriesResponse.class);
            if (response == null || response.billingEntries() == null) {
                throw new IllegalStateException("billing answered without entries");
            }
            page = response.billingEntries();
            page.stream()
                    .filter(Objects::nonNull)
                    .filter(entry -> entry.type() != null && COMMISSION_TYPES.contains(entry.type().id()))
                    .filter(entry -> entry.order() == null || checkoutFormId.equals(entry.order().id()))
                    .filter(entry -> entry.value() != null && entry.value().amount() != null)
                    .forEach(commission::add);
            offset += page.size();
        } while (page.size() == PAGE_SIZE);
        return commission;
    }

    /**
     * The entry's amount without VAT, unrounded. Billing books gross amounts with the VAT rate beside them, while the
     * app counts a payment's fee as a net cost (it adds VAT itself when it shows the gross cost). An entry with no
     * rate (VAT out of scope) counts as it is.
     */
    private static BigDecimal net(AllegroBillingEntriesResponse.Entry entry) {
        BigDecimal gross = new BigDecimal(entry.value().amount());
        if (entry.tax() == null || entry.tax().percentage() == null) {
            return gross;
        }
        BigDecimal divisor = BigDecimal.ONE.add(new BigDecimal(entry.tax().percentage()).movePointLeft(2));
        return gross.divide(divisor, 10, RoundingMode.HALF_UP);
    }

    /** The message with its cause: the HTTP client wraps a timeout or a refused connection in "HTTP request failed". */
    private static String describe(RuntimeException e) {
        Throwable cause = e.getCause();
        return cause == null || cause == e ? e.getMessage() : e.getMessage() + " (" + cause + ")";
    }

    private static int lineOf(AllegroBillingEntriesResponse.Entry entry, List<AllegroCheckoutForm.LineItem> lineItems) {
        if (entry.offer() == null || entry.offer().id() == null) {
            return 0;
        }
        for (int i = 0; i < lineItems.size(); i++) {
            AllegroCheckoutForm.Offer offer = lineItems.get(i).offer();
            if (offer != null && Objects.equals(entry.offer().id(), offer.id())) {
                return i;
            }
        }
        return 0;
    }
}
