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
                qty, new AllegroCheckoutForm.Price("100.00", "PLN"));
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
        assertEquals("application/vnd.allegro.beta.v1+json", headers.getValue().get("Content-Type"));
    }

    @Test
    void mapsReturnItemsThroughCheckoutFormExternalId() {
        // given
        stubReturnsPage(customerReturn("r-1", "CREATED",
                List.of(item("7680560740", 2, "NOT_AS_DESCRIBED", "Wrong colour")),
                List.of(new AllegroCustomerReturn.Parcel("0000123456", "INPOST"))));
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
}
