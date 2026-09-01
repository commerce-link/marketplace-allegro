package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.marketplace.api.MarketplaceReturnStatus;
import pl.commercelink.marketplace.api.ReturnRefund;
import pl.commercelink.marketplace.api.ReturnRejection;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllegroReturnsTest {

    private static final String ORDER_ID = "4a0a6511-dfd7-11eb-b6c4-d37454079c02";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RestApiWithRetry restApi;

    private static AllegroCustomerReturn customerReturn(String id, String status, List<AllegroCustomerReturn.Item> items,
                                                        List<AllegroCustomerReturn.Parcel> parcels) {
        return new AllegroCustomerReturn(id, ORDER_ID, "REF/" + id, status, "2026-08-20T12:13:53.464Z", false,
                items, parcels, null);
    }

    private static AllegroCustomerReturn.Item item(String offerId, long qty, String reasonType, String comment) {
        return new AllegroCustomerReturn.Item(offerId, qty, "Item " + offerId,
                new AllegroCustomerReturn.Price("100.00", "PLN"),
                reasonType == null ? null : new AllegroCustomerReturn.Reason(reasonType, comment));
    }

    private static AllegroCheckoutForm checkoutForm(AllegroCheckoutForm.LineItem... lineItems) {
        return new AllegroCheckoutForm(ORDER_ID, "READY_FOR_PROCESSING", null,
                new AllegroCheckoutForm.Payment("pay-1", "ONLINE", "2026-08-01T10:00:00Z"),
                null,
                new AllegroCheckoutForm.Delivery(null, new AllegroCheckoutForm.Cost("12.99", "PLN"), null, null),
                null, List.of(lineItems));
    }

    private static AllegroCheckoutForm.LineItem lineItem(String id, String offerId, String externalId, long qty) {
        return new AllegroCheckoutForm.LineItem(id,
                new AllegroCheckoutForm.Offer(offerId, "Item " + offerId,
                        externalId == null ? null : new AllegroCheckoutForm.External(externalId)),
                qty, new AllegroCheckoutForm.Price("100.00", "PLN"), null);
    }

    private static AllegroCheckoutForm.LineItem lineItemWithDeposit(String id, String offerId, long qty,
                                                                     String depositAmount, String depositCurrency) {
        return new AllegroCheckoutForm.LineItem(id,
                new AllegroCheckoutForm.Offer(offerId, "Item " + offerId, null),
                qty, new AllegroCheckoutForm.Price("100.00", "PLN"),
                new AllegroCheckoutForm.Deposit(new AllegroCheckoutForm.Cost(depositAmount, depositCurrency)));
    }

    private void stubReturnsPage(AllegroCustomerReturn... returns) {
        when(restApi.fetchWithAuthRetry(eq("/order/customer-returns"), anyMap(), anyMap(),
                eq(AllegroCustomerReturnsResponse.class)))
                .thenReturn(new AllegroCustomerReturnsResponse(returns.length, List.of(returns)));
    }

    private void stubCheckoutForm(AllegroCheckoutForm form) {
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID), anyMap(), eq(AllegroCheckoutForm.class)))
                .thenReturn(form);
    }

    private static AllegroCustomerReturn returnWithParcel(String waybill, String carrierId,
                                                          String transportingWaybill, String transportingCarrierId) {
        return customerReturn("r-1", "CREATED", List.of(item("111", 1, null, null)),
                List.of(new AllegroCustomerReturn.Parcel(waybill, carrierId, transportingWaybill, transportingCarrierId)));
    }

    private MarketplaceReturn firstMappedReturn(AllegroCustomerReturn ret) {
        stubReturnsPage(ret);
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));
        return new AllegroReturns(restApi, CLOCK).fetchReturns().get(0);
    }

    @Test
    void fetchesReturnsWithBetaHeadersAndCreatedAtWindow() {
        // given
        stubReturnsPage();
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        returns.fetchReturns();

        // then
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(restApi).fetchWithAuthRetry(eq("/order/customer-returns"), params.capture(), headers.capture(),
                eq(AllegroCustomerReturnsResponse.class));
        assertEquals("2026-06-28T12:00:00Z", params.getValue().get("createdAt.gte"));
        assertEquals("1000", params.getValue().get("limit"));
        assertEquals("0", params.getValue().get("offset"));
        assertEquals("application/vnd.allegro.beta.v1+json", headers.getValue().get("Accept"));
        assertNull(headers.getValue().get("Content-Type"));
    }

    @Test
    void prefersTheTransportingCarrierOverAllegroOwnWaybill() {
        // given: an Allegro return label — the physical carrier differs from the Allegro one
        // (docs: transporting* is null only when a single carrier is involved)
        AllegroCustomerReturn ret = returnWithParcel("ALLEGRO-WB", "ALLEGRO", "2171143568953", "ORLEN");

        // when
        MarketplaceReturn mapped = firstMappedReturn(ret);

        // then
        assertEquals("2171143568953", mapped.parcels().get(0).trackingNo());
        assertEquals("ORLEN", mapped.parcels().get(0).carrierId());
    }

    @Test
    void mapsReturnItemsThroughCheckoutFormExternalId() {
        // given
        stubReturnsPage(customerReturn("r-1", "CREATED",
                List.of(item("7680560740", 2, "NOT_AS_DESCRIBED", "Wrong colour")),
                List.of(new AllegroCustomerReturn.Parcel("0000123456", "INPOST", null, null))));
        stubCheckoutForm(checkoutForm(lineItem("li-1", "7680560740", "SKU-1", 3)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        List<MarketplaceReturn> result = returns.fetchReturns();

        // then
        assertEquals(1, result.size());
        MarketplaceReturn ret = result.get(0);
        assertEquals("r-1", ret.externalReturnId());
        assertEquals(ORDER_ID, ret.externalOrderId());
        assertEquals("REF/r-1", ret.referenceNumber());
        assertEquals(MarketplaceReturnStatus.DECLARED, ret.status());
        assertEquals(LocalDateTime.of(2026, 8, 20, 12, 13, 53, 464_000_000), ret.createdAt());
        MarketplaceReturn.Item item = ret.items().get(0);
        assertEquals("SKU-1", item.manufacturerCode());
        assertEquals(2, item.quantity());
        assertEquals(new BigDecimal("100.00"), item.unitPriceGross());
        assertEquals("NOT_AS_DESCRIBED: Wrong colour", item.reason());
        assertEquals("0000123456", ret.parcels().get(0).trackingNo());
        assertEquals("INPOST", ret.parcels().get(0).carrierId());
    }

    @Test
    void fallsBackToOfferIdWhenExternalIdMissingOrLineItemUnknown() {
        // given
        stubReturnsPage(customerReturn("r-1", "CREATED",
                List.of(item("111", 1, "MISTAKE", null), item("999", 1, null, null)), List.of()));
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        List<MarketplaceReturn.Item> items = returns.fetchReturns().get(0).items();

        // then
        assertEquals("111", items.get(0).manufacturerCode());
        assertEquals("MISTAKE", items.get(0).reason());
        assertEquals("999", items.get(1).manufacturerCode());
        assertNull(items.get(1).reason());
    }

    @Test
    void toleratesNonNumericPriceInFetchedReturn() {
        // given
        AllegroCustomerReturn.Item badPrice = new AllegroCustomerReturn.Item("111", 1, "Item 111",
                new AllegroCustomerReturn.Price("N/A", "PLN"), null);
        stubReturnsPage(customerReturn("r-1", "CREATED", List.of(badPrice), List.of()));
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));

        // when
        MarketplaceReturn.Item item = new AllegroReturns(restApi, CLOCK).fetchReturns().get(0).items().get(0);

        // then
        assertNull(item.unitPriceGross());
    }

    @ParameterizedTest
    @CsvSource({
            "CREATED, DECLARED", "DISPATCHED, IN_TRANSIT", "IN_TRANSIT, IN_TRANSIT", "DELIVERED, DELIVERED",
            "WAREHOUSE_DELIVERED, DELIVERED", "WAREHOUSE_VERIFICATION, DELIVERED", "FINISHED, REFUNDED",
            "FINISHED_APT, REFUNDED", "COMMISSION_REFUND_CLAIMED, REFUNDED", "COMMISSION_REFUNDED, REFUNDED",
            "REJECTED, REJECTED"
    })
    void mapsAllegroStatuses(String allegroStatus, MarketplaceReturnStatus expected) {
        // given
        stubReturnsPage(customerReturn("r-1", allegroStatus, List.of(item("111", 1, null, null)), List.of()));
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));

        // when
        List<MarketplaceReturn> result = new AllegroReturns(restApi, CLOCK).fetchReturns();

        // then
        assertEquals(expected, result.get(0).status());
    }

    @Test
    void skipsUnknownStatusFulfillmentReturnsAndReturnsWithoutOrderOrItems() {
        // given
        AllegroCustomerReturn fulfillment = new AllegroCustomerReturn("r-f", ORDER_ID, null, "CREATED",
                "2026-08-20T12:13:53.464Z", true, List.of(item("111", 1, null, null)), List.of(), null);
        AllegroCustomerReturn noOrder = new AllegroCustomerReturn("r-o", null, null, "CREATED",
                "2026-08-20T12:13:53.464Z", false, List.of(item("111", 1, null, null)), List.of(), null);
        stubReturnsPage(customerReturn("r-u", "SOMETHING_NEW", List.of(item("111", 1, null, null)), List.of()),
                fulfillment, noOrder, customerReturn("r-e", "CREATED", List.of(), List.of()));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        List<MarketplaceReturn> result = returns.fetchReturns();

        // then
        assertTrue(result.isEmpty());
        verify(restApi, never()).fetchWithAuthRetry(startsWith("/order/checkout-forms/"), anyMap(), eq(AllegroCheckoutForm.class));
    }

    @Test
    void skipsReturnWhenCheckoutFormIsMissing() {
        // given
        stubReturnsPage(customerReturn("r-1", "CREATED", List.of(item("111", 1, null, null)), List.of()));
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID), anyMap(), eq(AllegroCheckoutForm.class)))
                .thenThrow(new pl.commercelink.rest.client.HttpClientException(404, "Not found"));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        List<MarketplaceReturn> result = returns.fetchReturns();

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchesCheckoutFormOncePerOrderWithinOnePoll() {
        // given
        stubReturnsPage(customerReturn("r-1", "CREATED", List.of(item("111", 1, null, null)), List.of()),
                customerReturn("r-2", "DELIVERED", List.of(item("111", 1, null, null)), List.of()));
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 5)));

        // when
        List<MarketplaceReturn> result = new AllegroReturns(restApi, CLOCK).fetchReturns();

        // then
        assertEquals(2, result.size());
        verify(restApi, times(1)).fetchWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID), anyMap(), eq(AllegroCheckoutForm.class));
    }

    @Test
    void paginatesByOffsetUntilCountIsReached() {
        // given
        AllegroCustomerReturn first = customerReturn("r-1", "CREATED", List.of(item("111", 1, null, null)), List.of());
        AllegroCustomerReturn second = customerReturn("r-2", "CREATED", List.of(item("111", 1, null, null)), List.of());
        when(restApi.fetchWithAuthRetry(eq("/order/customer-returns"), argThat((Map<String, String> p) -> "0".equals(p.get("offset"))), anyMap(),
                eq(AllegroCustomerReturnsResponse.class)))
                .thenReturn(new AllegroCustomerReturnsResponse(2, List.of(first)));
        when(restApi.fetchWithAuthRetry(eq("/order/customer-returns"), argThat((Map<String, String> p) -> "1".equals(p.get("offset"))), anyMap(),
                eq(AllegroCustomerReturnsResponse.class)))
                .thenReturn(new AllegroCustomerReturnsResponse(2, List.of(second)));
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 5)));

        // when
        List<MarketplaceReturn> result = new AllegroReturns(restApi, CLOCK).fetchReturns();

        // then
        assertEquals(List.of("r-1", "r-2"), result.stream().map(MarketplaceReturn::externalReturnId).toList());
    }

    @Test
    void refundPostsQuantityLineItemsResolvedByManufacturerCode() {
        // given
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", "SKU-1", 3), lineItem("li-2", "222", null, 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);
        ReturnRefund refund = new ReturnRefund(
                List.of(new ReturnRefund.Item("SKU-1", 2), new ReturnRefund.Item("222", 1)), false, "cmd-1", null);

        // when
        returns.refundReturn(ORDER_ID, "r-1", refund);

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        AllegroRefundRequest request = (AllegroRefundRequest) body.getValue();
        assertEquals("pay-1", request.payment().id());
        assertEquals(ORDER_ID, request.order().id());
        assertEquals("cmd-1", request.commandId());
        assertEquals("REFUND", request.reason());
        assertEquals(2, request.lineItems().size());
        assertEquals("li-1", request.lineItems().get(0).id());
        assertEquals("QUANTITY", request.lineItems().get(0).type());
        assertEquals(2, request.lineItems().get(0).quantity());
        assertEquals("li-2", request.lineItems().get(1).id());
        assertNull(request.delivery());
        assertEquals("Zwrot r-1", request.sellerComment());
    }

    @Test
    void refundUsesReferenceNumberInSellerCommentWhenPresent() {
        // given: the buyer's own reference is more meaningful to them than our internal return id
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);
        ReturnRefund refund = new ReturnRefund(List.of(new ReturnRefund.Item("111", 1)), false, "cmd-1", "XGQX/2026");

        // when
        returns.refundReturn(ORDER_ID, "r-1", refund);

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        assertEquals("Zwrot XGQX/2026", ((AllegroRefundRequest) body.getValue()).sellerComment());
    }

    @Test
    void refundIncludesDepositForDepositBearingLineItem() {
        // given
        stubCheckoutForm(checkoutForm(lineItemWithDeposit("li-1", "111", 1, "1.00", "PLN"),
                lineItem("li-2", "222", null, 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);
        ReturnRefund refund = new ReturnRefund(
                List.of(new ReturnRefund.Item("111", 1), new ReturnRefund.Item("222", 1)), false, "cmd-1", null);

        // when
        returns.refundReturn(ORDER_ID, "r-1", refund);

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        AllegroRefundRequest request = (AllegroRefundRequest) body.getValue();
        assertEquals(1, request.deposits().size());
        assertEquals("li-1", request.deposits().get(0).lineItemId());
        assertEquals("1.00", request.deposits().get(0).totalValue().amount());
        assertEquals("PLN", request.deposits().get(0).totalValue().currency());
    }

    @Test
    void refundOmitsDepositsWhenNoLineItemCarriesOne() {
        // given
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        returns.refundReturn(ORDER_ID, "r-1", new ReturnRefund(List.of(new ReturnRefund.Item("111", 1)), false, "cmd-1", null));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        assertNull(((AllegroRefundRequest) body.getValue()).deposits());
    }

    @Test
    void refundScalesDepositByTheRefundedQuantity() {
        // given: a 3-unit deposit-bearing line item, all 3 units being refunded - deposit.value is treated
        // as per unit (same convention as LineItem.price elsewhere), so the total must scale with quantity
        stubCheckoutForm(checkoutForm(lineItemWithDeposit("li-1", "111", 3, "1.00", "PLN")));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);
        ReturnRefund refund = new ReturnRefund(List.of(new ReturnRefund.Item("111", 3)), false, "cmd-1", null);

        // when
        returns.refundReturn(ORDER_ID, "r-1", refund);

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        AllegroRefundRequest request = (AllegroRefundRequest) body.getValue();
        assertEquals(1, request.deposits().size());
        assertEquals("li-1", request.deposits().get(0).lineItemId());
        assertEquals("3.00", request.deposits().get(0).totalValue().amount());
        assertEquals("PLN", request.deposits().get(0).totalValue().currency());
    }

    @Test
    void refundOmitsANonNumericOrNonPositiveDeposit() {
        // given: one line item with a garbled deposit value, one with a zero deposit
        stubCheckoutForm(checkoutForm(lineItemWithDeposit("li-1", "111", 1, "not-a-number", "PLN"),
                lineItemWithDeposit("li-2", "222", 1, "0.00", "PLN")));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);
        ReturnRefund refund = new ReturnRefund(
                List.of(new ReturnRefund.Item("111", 1), new ReturnRefund.Item("222", 1)), false, "cmd-1", null);

        // when
        returns.refundReturn(ORDER_ID, "r-1", refund);

        // then: neither garbled nor zero/negative deposits are forwarded into the money POST
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        assertNull(((AllegroRefundRequest) body.getValue()).deposits());
    }

    @Test
    void refundMergesDuplicateLineItemIdsIntoOneEntry() {
        // given: two refund items resolving to the same checkout-form line item
        stubCheckoutForm(checkoutForm(lineItem("li-1", "offer-1", "sku-a", 3)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        returns.refundReturn(ORDER_ID, "ret-1", new ReturnRefund(
                List.of(new ReturnRefund.Item("sku-a", 1), new ReturnRefund.Item("sku-a", 2)), false, "cmd-1", null));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        AllegroRefundRequest sent = (AllegroRefundRequest) body.getValue();
        assertEquals(1, sent.lineItems().size());
        assertEquals("li-1", sent.lineItems().get(0).id());
        assertEquals(3, sent.lineItems().get(0).quantity());
    }

    @Test
    void refundIncludesDeliveryCostWhenRequested() {
        // given
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        returns.refundReturn(ORDER_ID, "r-1", new ReturnRefund(List.of(new ReturnRefund.Item("111", 1)), true, "cmd-1", null));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        AllegroRefundRequest request = (AllegroRefundRequest) body.getValue();
        assertEquals("12.99", request.delivery().value().amount());
        assertEquals("PLN", request.delivery().value().currency());
    }

    @Test
    void refundSkipsDeliveryWhenCostIsZero() {
        // given
        AllegroCheckoutForm form = new AllegroCheckoutForm(ORDER_ID, "READY_FOR_PROCESSING", null,
                new AllegroCheckoutForm.Payment("pay-1", "ONLINE", null), null,
                new AllegroCheckoutForm.Delivery(null, new AllegroCheckoutForm.Cost("0.00", "PLN"), null, null),
                null, List.of(lineItem("li-1", "111", null, 1)));
        stubCheckoutForm(form);

        // when
        new AllegroReturns(restApi, CLOCK).refundReturn(ORDER_ID, "r-1",
                new ReturnRefund(List.of(new ReturnRefund.Item("111", 1)), true, "cmd-1", null));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        assertNull(((AllegroRefundRequest) body.getValue()).delivery());
    }

    @Test
    void refundSkipsDeliveryWhenCostIsNotNumeric() {
        // given
        AllegroCheckoutForm form = new AllegroCheckoutForm(ORDER_ID, "READY_FOR_PROCESSING", null,
                new AllegroCheckoutForm.Payment("pay-1", "ONLINE", null), null,
                new AllegroCheckoutForm.Delivery(null, new AllegroCheckoutForm.Cost("N/A", "PLN"), null, null),
                null, List.of(lineItem("li-1", "111", null, 1)));
        stubCheckoutForm(form);

        // when
        new AllegroReturns(restApi, CLOCK).refundReturn(ORDER_ID, "r-1",
                new ReturnRefund(List.of(new ReturnRefund.Item("111", 1)), true, "cmd-1", null));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        assertNull(((AllegroRefundRequest) body.getValue()).delivery());
    }

    @Test
    void refundFailsLoudWhenLineItemCannotBeResolved() {
        // given
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", null, 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when / then
        assertThrows(IllegalStateException.class, () -> returns.refundReturn(ORDER_ID, "r-1",
                new ReturnRefund(List.of(new ReturnRefund.Item("UNKNOWN", 1)), false, "cmd-1", null)));
        verify(restApi, never()).postWithAuthRetry(eq("/payments/refunds"), any(), eq(AllegroRefundResponse.class));
    }

    @Test
    void refundFallsBackToNormalisedComparisonForLegacyOrders() {
        // given: a legacy order sends an uppercased key, while Allegro holds the raw seller SKU
        stubCheckoutForm(checkoutForm(lineItem("li-1", "111", "k7m2xq9pz4", 1)));
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        returns.refundReturn(ORDER_ID, "ret-1",
                new ReturnRefund(List.of(new ReturnRefund.Item("K7M2XQ9PZ4", 1)), false, "cmd-1", null));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/payments/refunds"), body.capture(), eq(AllegroRefundResponse.class));
        AllegroRefundRequest request = (AllegroRefundRequest) body.getValue();
        assertEquals("li-1", request.lineItems().get(0).id());
    }

    @Test
    void refundFailsLoudWhenCheckoutFormMissing() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID), anyMap(), eq(AllegroCheckoutForm.class)))
                .thenThrow(new pl.commercelink.rest.client.HttpClientException(404, "Not found"));

        // when / then
        assertThrows(IllegalStateException.class, () -> new AllegroReturns(restApi, CLOCK).refundReturn(ORDER_ID, "r-1",
                new ReturnRefund(List.of(new ReturnRefund.Item("111", 1)), false, "cmd-1", null)));
    }

    private void stubReturnDetails(String status, AllegroCustomerReturn.Rejection rejection) {
        when(restApi.fetchWithAuthRetry(eq("/order/customer-returns/r-1"), anyMap(), anyMap(), eq(AllegroCustomerReturn.class)))
                .thenReturn(new AllegroCustomerReturn("r-1", ORDER_ID, null, status, null, false, List.of(), List.of(), rejection));
    }

    @Test
    void rejectPostsRefundRejectedWithReasonUsingBetaHeaders() {
        // given
        stubReturnDetails("DELIVERED", null);
        AllegroReturns returns = new AllegroReturns(restApi, CLOCK);

        // when
        returns.rejectReturn("r-1", new ReturnRejection("Item damaged by buyer"));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(restApi).postWithAuthRetry(eq("/order/customer-returns/r-1/rejection"), body.capture(), headers.capture(),
                eq(AllegroCustomerReturn.class));
        AllegroReturnRejectionRequest request = (AllegroReturnRejectionRequest) body.getValue();
        assertEquals("REFUND_REJECTED", request.rejection().code());
        assertEquals("Item damaged by buyer", request.rejection().reason());
        assertEquals("application/vnd.allegro.beta.v1+json", headers.getValue().get("Accept"));
    }

    @Test
    void rejectTruncatesReasonTo250Characters() {
        // given
        stubReturnDetails("CREATED", null);
        String longReason = "x".repeat(300);

        // when
        new AllegroReturns(restApi, CLOCK).rejectReturn("r-1", new ReturnRejection(longReason));

        // then
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/order/customer-returns/r-1/rejection"), body.capture(), anyMap(),
                eq(AllegroCustomerReturn.class));
        assertEquals(250, ((AllegroReturnRejectionRequest) body.getValue()).rejection().reason().length());
    }

    @Test
    void rejectionRequiresANonEmptyReason() {
        // given: the docs declare minLength 1 for REFUND_REJECTED
        stubReturnDetails("DELIVERED", null);

        // when / then
        assertThrows(IllegalArgumentException.class,
                () -> new AllegroReturns(restApi, CLOCK).rejectReturn("r-1", new ReturnRejection("  ")));
        verify(restApi, never()).postWithAuthRetry(contains("/rejection"), any(), any(), any());
    }

    @Test
    void rejectIsNoOpWhenAlreadyRejected() {
        // given
        stubReturnDetails("REJECTED", new AllegroCustomerReturn.Rejection("REFUND_REJECTED", "earlier", null));

        // when
        new AllegroReturns(restApi, CLOCK).rejectReturn("r-1", new ReturnRejection("again"));

        // then
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), anyMap(), any());
    }

    @Test
    void rejectIsNoOpWhenAlreadyRefunded() {
        // given
        stubReturnDetails("FINISHED", null);

        // when
        new AllegroReturns(restApi, CLOCK).rejectReturn("r-1", new ReturnRejection("too late"));

        // then
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), anyMap(), any());
    }
}
