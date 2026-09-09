package pl.commercelink.marketplace.allegro;

import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.marketplace.api.MarketplaceReturns;
import pl.commercelink.marketplace.api.ReturnRefund;
import pl.commercelink.marketplace.api.ReturnRejection;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

class AllegroReturns implements MarketplaceReturns {

    private static final System.Logger LOGGER = System.getLogger(AllegroReturns.class.getName());

    private static final String BETA_MEDIA_TYPE = "application/vnd.allegro.beta.v1+json";
    private static final Map<String, String> BETA_ACCEPT_ONLY = Map.of("Accept", BETA_MEDIA_TYPE);
    private static final Map<String, String> BETA_HEADERS = Map.of("Accept", BETA_MEDIA_TYPE, "Content-Type", BETA_MEDIA_TYPE);
    private static final int RETURNS_WINDOW_DAYS = 60;
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_REJECTION_REASON = 250;

    private static final String CUSTOMER_RETURNS = "/order/customer-returns";
    private static final String CHECKOUT_FORMS = "/order/checkout-forms/";
    private static final String REFUND_REASON = "REFUND";
    private static final String LINE_ITEM_TYPE_QUANTITY = "QUANTITY";

    private final RestApiWithRetry restApi;
    private final Clock clock;

    AllegroReturns(RestApiWithRetry restApi) {
        this(restApi, Clock.systemUTC());
    }

    AllegroReturns(RestApiWithRetry restApi, Clock clock) {
        this.restApi = restApi;
        this.clock = clock;
    }

    @Override
    public List<MarketplaceReturn> fetchReturns() {
        List<AllegroCustomerReturn> page = fetchAllPages();
        Map<String, Optional<AllegroCheckoutForm>> formsByOrder = new HashMap<>();
        List<MarketplaceReturn> result = new ArrayList<>();
        for (AllegroCustomerReturn ret : page) {
            Optional<MarketplaceReturnStatus> status = mapStatus(ret.status());
            if (!isImportable(ret) || status.isEmpty()) {
                LOGGER.log(System.Logger.Level.WARNING, "Skipping Allegro customer return {0}: status={1}, fulfillment={2}",
                        ret.id(), ret.status(), ret.isFulfillment());
                continue;
            }
            Optional<AllegroCheckoutForm> form = formsByOrder.computeIfAbsent(ret.orderId(), this::fetchCheckoutForm);
            if (form.isEmpty()) {
                LOGGER.log(System.Logger.Level.WARNING, "Skipping Allegro customer return {0}: checkout form {1} not found",
                        ret.id(), ret.orderId());
                continue;
            }
            result.add(toMarketplaceReturn(ret, status.get(), form.get()));
        }
        return result;
    }

    @Override
    public void refundReturn(String externalOrderId, String externalReturnId, ReturnRefund refund) {
        AllegroCheckoutForm form = fetchCheckoutForm(externalOrderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Allegro checkout form " + externalOrderId + " not found for return " + externalReturnId));
        if (form.payment() == null || form.payment().id() == null) {
            throw new IllegalStateException("Allegro checkout form " + externalOrderId + " has no payment id");
        }
        if (form.lineItems() == null) {
            throw new IllegalStateException("Allegro checkout form " + externalOrderId + " has no line items");
        }
        // A payload with a repeated lineItems[].id is rejected by Allegro; merge defensively so no caller
        // can emit one. Line item ids are unique within a checkout form, so the record is a safe map key.
        Map<AllegroCheckoutForm.LineItem, Integer> quantityByLineItem = new LinkedHashMap<>();
        for (ReturnRefund.Item item : refund.items()) {
            quantityByLineItem.merge(resolveLineItem(form, item.offerKey(), externalReturnId), item.quantity(), Integer::sum);
        }
        List<AllegroRefundRequest.LineItem> lineItems = new ArrayList<>();
        List<AllegroRefundRequest.Deposit> deposits = new ArrayList<>();
        for (Map.Entry<AllegroCheckoutForm.LineItem, Integer> entry : quantityByLineItem.entrySet()) {
            AllegroCheckoutForm.LineItem lineItem = entry.getKey();
            int quantity = entry.getValue();
            // Fail before the POST: Allegro rejects an over-refund with a 422 that only surfaces after the
            // caller has already restocked the goods on the app side.
            if (quantity > lineItem.quantity()) {
                throw new IllegalStateException("Refund of " + quantity + " units requested for line item "
                        + lineItem.id() + " of order " + externalOrderId + ", but only " + lineItem.quantity()
                        + " were ordered");
            }
            lineItems.add(new AllegroRefundRequest.LineItem(lineItem.id(), LINE_ITEM_TYPE_QUANTITY, quantity));
            depositOf(lineItem, quantity).ifPresent(d -> deposits.add(new AllegroRefundRequest.Deposit(lineItem.id(), d)));
        }
        AllegroRefundRequest request = new AllegroRefundRequest(
                new AllegroRefundRequest.Ref(form.payment().id()),
                new AllegroRefundRequest.Ref(externalOrderId),
                refund.idempotencyKey(),
                REFUND_REASON,
                lineItems,
                deposits.isEmpty() ? null : deposits,
                refund.refundDelivery() ? deliveryRefund(form) : null,
                "Zwrot " + externalReturnId);
        AllegroRefundResponse response = restApi.postWithAuthRetry("/payments/refunds", request, AllegroRefundResponse.class);
        if (response == null) {
            LOGGER.log(System.Logger.Level.WARNING, "Allegro returned an empty body for the refund of return {0}", externalReturnId);
        }
    }

    @Override
    public void rejectReturn(String externalReturnId, ReturnRejection rejection) {
        AllegroCustomerReturn current = restApi.fetchWithAuthRetry(CUSTOMER_RETURNS + "/" + externalReturnId,
                Map.of(), BETA_ACCEPT_ONLY, AllegroCustomerReturn.class);
        if (current.rejection() != null || "REJECTED".equals(current.status())) {
            return;
        }
        Optional<MarketplaceReturnStatus> status = mapStatus(current.status());
        if (status.isPresent() && status.get() == MarketplaceReturnStatus.REFUNDED) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Allegro return {0} is already refunded ({1}); rejection skipped", externalReturnId, current.status());
            return;
        }
        String reason = rejection.reason() == null ? "" : rejection.reason().trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Allegro requires a rejection reason (1-250 chars) for return "
                    + externalReturnId);
        }
        if (reason.length() > MAX_REJECTION_REASON) {
            reason = reason.substring(0, MAX_REJECTION_REASON);
        }
        restApi.postWithAuthRetry(CUSTOMER_RETURNS + "/" + externalReturnId + "/rejection",
                AllegroReturnRejectionRequest.refundRejected(reason), BETA_HEADERS, AllegroCustomerReturn.class);
    }

    private List<AllegroCustomerReturn> fetchAllPages() {
        String createdAtFrom = DateTimeFormatter.ISO_INSTANT.format(
                Instant.now(clock).minus(RETURNS_WINDOW_DAYS, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
        List<AllegroCustomerReturn> all = new ArrayList<>();
        int offset = 0;
        AllegroCustomerReturnsResponse response;
        do {
            Map<String, String> params = new HashMap<>();
            params.put("createdAt.gte", createdAtFrom);
            params.put("limit", String.valueOf(PAGE_SIZE));
            params.put("offset", String.valueOf(offset));
            response = restApi.fetchWithAuthRetry(CUSTOMER_RETURNS, params, BETA_ACCEPT_ONLY, AllegroCustomerReturnsResponse.class);
            List<AllegroCustomerReturn> returns = response.customerReturns() == null ? List.of() : response.customerReturns();
            all.addAll(returns);
            offset += returns.size();
            if (returns.isEmpty()) {
                break;
            }
        } while (offset < response.count());
        return all;
    }

    private static boolean isImportable(AllegroCustomerReturn ret) {
        return !Boolean.TRUE.equals(ret.isFulfillment())
                && ret.orderId() != null
                && ret.items() != null && !ret.items().isEmpty();
    }

    // package-private for reuse by refund/reject
    static Optional<MarketplaceReturnStatus> mapStatus(String status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case "CREATED" -> Optional.of(MarketplaceReturnStatus.DECLARED);
            case "DISPATCHED", "IN_TRANSIT" -> Optional.of(MarketplaceReturnStatus.IN_TRANSIT);
            case "DELIVERED", "WAREHOUSE_DELIVERED", "WAREHOUSE_VERIFICATION" -> Optional.of(MarketplaceReturnStatus.DELIVERED);
            case "FINISHED", "FINISHED_APT", "COMMISSION_REFUND_CLAIMED", "COMMISSION_REFUNDED" ->
                    Optional.of(MarketplaceReturnStatus.REFUNDED);
            case "REJECTED" -> Optional.of(MarketplaceReturnStatus.REJECTED);
            default -> Optional.empty();
        };
    }

    Optional<AllegroCheckoutForm> fetchCheckoutForm(String orderId) {
        try {
            return Optional.of(restApi.fetchWithAuthRetry(CHECKOUT_FORMS + orderId, Map.of(), AllegroCheckoutForm.class));
        } catch (HttpClientException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    private static MarketplaceReturn toMarketplaceReturn(AllegroCustomerReturn ret, MarketplaceReturnStatus status,
                                                         AllegroCheckoutForm form) {
        List<MarketplaceReturn.Item> items = ret.items().stream()
                .map(item -> new MarketplaceReturn.Item(
                        manufacturerCodeForOffer(form, item.offerId()),
                        (int) item.quantity(),
                        formatReason(item.reason())))
                .toList();
        List<MarketplaceReturn.Parcel> parcels = ret.parcels() == null ? List.of() : ret.parcels().stream()
                .map(p -> new MarketplaceReturn.Parcel(
                        firstNonBlank(p.transportingWaybill(), p.waybill()),
                        firstNonBlank(p.transportingCarrierId(), p.carrierId())))
                .filter(p -> p.trackingNo() != null && !p.trackingNo().isBlank())
                .toList();
        return new MarketplaceReturn(ret.id(), ret.orderId(), ret.referenceNumber(), status,
                parseUtc(ret.createdAt()), items, parcels);
    }

    /** Same rule as order import: seller SKU (offer.external.id) when set, otherwise the offer id. */
    private static String manufacturerCodeForOffer(AllegroCheckoutForm form, String offerId) {
        if (form.lineItems() != null) {
            for (AllegroCheckoutForm.LineItem lineItem : form.lineItems()) {
                if (lineItem.offer() != null && offerId.equals(lineItem.offer().id())) {
                    // Seller SKU rule is shared with order import
                    return AllegroOrdersImport.resolveManufacturerCode(lineItem.offer());
                }
            }
        }
        return offerId;
    }

    /** Allegro leaves the transporting fields null when a single carrier handles the parcel. */
    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private static String formatReason(AllegroCustomerReturn.Reason reason) {
        if (reason == null || reason.type() == null) {
            return null;
        }
        if (reason.userComment() == null || reason.userComment().isBlank()) {
            return reason.type();
        }
        return reason.type() + ": " + reason.userComment();
    }

    private static LocalDateTime parseUtc(String isoInstant) {
        return isoInstant == null ? null : LocalDateTime.ofInstant(Instant.parse(isoInstant), ZoneOffset.UTC);
    }

    /** Exact key match always wins; the normalised comparison only serves orders imported before the app persisted the raw key. */
    private static AllegroCheckoutForm.LineItem resolveLineItem(AllegroCheckoutForm form, String offerKey,
                                                                String externalReturnId) {
        String normalisedKey = normaliseCode(offerKey);
        AllegroCheckoutForm.LineItem normalisedMatch = null;
        for (AllegroCheckoutForm.LineItem lineItem : form.lineItems()) {
            if (lineItem.offer() == null) {
                continue;
            }
            String code = AllegroOrdersImport.resolveManufacturerCode(lineItem.offer());
            if (offerKey.equals(code)) {
                return lineItem;
            }
            if (normalisedMatch == null && normalisedKey.equals(normaliseCode(code))) {
                normalisedMatch = lineItem;
            }
        }
        if (normalisedMatch != null) {
            return normalisedMatch;
        }
        throw new IllegalStateException("No Allegro line item matches offer key " + offerKey
                + " in order " + form.id() + " for return " + externalReturnId);
    }

    /**
     * Mirrors UnifiedProductIdentifiers.unifyMfn in the app. Duplicated on purpose: this module has no
     * dependency on commercelink-commons and is meant to stay thin.
     */
    private static String normaliseCode(String code) {
        return code == null ? "" : code.trim().replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private static AllegroRefundRequest.Delivery deliveryRefund(AllegroCheckoutForm form) {
        if (form.delivery() == null || form.delivery().cost() == null) {
            return null;
        }
        AllegroCheckoutForm.Cost cost = form.delivery().cost();
        BigDecimal amount = parseAmount(cost.amount());
        if (amount == null || amount.signum() <= 0) {
            return null;
        }
        return new AllegroRefundRequest.Delivery(new AllegroRefundRequest.Money(cost.amount(), cost.currency()));
    }

    /**
     * checkout-forms.lineItems[].deposit.value is treated here as a PER-UNIT amount - unconfirmed, to be
     * verified on the sandbox - by symmetry with how this module already treats LineItem.price elsewhere
     * (AllegroOrdersImport pairs price with quantity as a unit price). The refund request field is named
     * totalValue, so a multi-unit refund must scale it by the refunded quantity. A null or non-positive
     * value is omitted rather than forwarded into a money POST, like deliveryRefund does for the delivery cost.
     */
    private static Optional<AllegroRefundRequest.Money> depositOf(AllegroCheckoutForm.LineItem lineItem, int quantity) {
        if (lineItem.deposit() == null || lineItem.deposit().value() == null) {
            return Optional.empty();
        }
        AllegroCheckoutForm.Cost value = lineItem.deposit().value();
        BigDecimal perUnit = parseAmount(value.amount());
        if (perUnit == null || perUnit.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal total = perUnit.multiply(BigDecimal.valueOf(quantity));
        return Optional.of(new AllegroRefundRequest.Money(total.toPlainString(), value.currency()));
    }

    private static BigDecimal parseAmount(String amount) {
        if (amount == null) {
            return null;
        }
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not parse Allegro amount {0}", amount);
            return null;
        }
    }
}
