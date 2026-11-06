package pl.commercelink.marketplace.allegro;

import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * order's fee is the marketplace's commission on the sale (the same thing Empik and Morele report). Amounts are gross,
 * as billing books them. The commission is read once, when the order is imported; anything going wrong yields zero so
 * the order is still imported.
 */
class AllegroOrderCommission {

    private static final System.Logger LOGGER = System.getLogger(AllegroOrderCommission.class.getName());
    private static final String ENDPOINT = "/billing/billing-entries";
    private static final int PAGE_SIZE = 100;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /**
     * Billing types that are a commission on the sale (ids from {@code GET /billing/billing-types}): sales commission,
     * on featured offers, on additional services, campaign sales and participation commissions, the unit transaction
     * fee and the alcohol category transaction fee.
     */
    static final Set<String> COMMISSION_TYPES = Set.of(
            "SUC", "FSF", "ASC", "BRG", "BC1", "BC2", "BC3", "BC4", "BC5", "USF", "KAC");

    private final RestApiWithRetry restApi;

    AllegroOrderCommission(RestApiWithRetry restApi) {
        this.restApi = restApi;
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
                BigDecimal commission = new BigDecimal(entry.value().amount()).negate();
                int line = lineOf(entry, lineItems);
                perLine.set(line, perLine.get(line).add(commission));
            }
        } catch (RuntimeException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Allegro billing unavailable for checkout form {0}: {1}",
                    checkoutFormId, e.getMessage());
            return new ArrayList<>(Collections.nCopies(lineItems.size(), ZERO));
        }
        if (entries.isEmpty()) {
            LOGGER.log(System.Logger.Level.INFO, "No Allegro commission yet for checkout form {0}", checkoutFormId);
        }
        return perLine.stream()
                .map(value -> value.signum() < 0 ? ZERO : value.setScale(2, RoundingMode.HALF_UP))
                .toList();
    }

    private List<AllegroBillingEntriesResponse.Entry> commissionEntries(String checkoutFormId) {
        List<AllegroBillingEntriesResponse.Entry> commission = new ArrayList<>();
        int offset = 0;
        List<AllegroBillingEntriesResponse.Entry> page;
        do {
            AllegroBillingEntriesResponse response = restApi.fetchWithAuthRetry(ENDPOINT,
                    Map.of("order.id", checkoutFormId, "limit", String.valueOf(PAGE_SIZE), "offset", String.valueOf(offset)),
                    AllegroBillingEntriesResponse.class);
            page = response == null || response.billingEntries() == null ? List.of() : response.billingEntries();
            page.stream()
                    .filter(entry -> entry.type() != null && COMMISSION_TYPES.contains(entry.type().id()))
                    .filter(entry -> entry.order() == null || checkoutFormId.equals(entry.order().id()))
                    .filter(entry -> entry.value() != null && entry.value().amount() != null)
                    .forEach(commission::add);
            offset += page.size();
        } while (page.size() == PAGE_SIZE);
        return commission;
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
