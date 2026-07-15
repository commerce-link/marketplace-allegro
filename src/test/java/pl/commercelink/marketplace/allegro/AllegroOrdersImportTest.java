package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllegroOrdersImportTest {

    @Mock
    private RestApiWithRetry restApi;

    private AllegroCheckoutForm paidForm(String id) {
        return new AllegroCheckoutForm(
                id,
                "READY_FOR_PROCESSING",
                new AllegroCheckoutForm.Buyer("b-1", "buyer+abc@user.allegromail.pl", "buyer1", "Jan", "Kowalski", "+48123123123"),
                new AllegroCheckoutForm.Payment("pay-1", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                new AllegroCheckoutForm.Delivery(
                        new AllegroCheckoutForm.DeliveryAddress("Jan", "Kowalski", null, "Prosta 1",
                                "Warszawa", "00-001", "PL", "+48123123123"),
                        new AllegroCheckoutForm.Cost("15.99", "PLN"),
                        null),
                new AllegroCheckoutForm.Invoice(false, null),
                List.of(new AllegroCheckoutForm.LineItem(
                        new AllegroCheckoutForm.Offer("offer-1", "Laptop X", new AllegroCheckoutForm.External("SKU-1")),
                        2,
                        new AllegroCheckoutForm.Price("2500.00", "PLN"))));
    }

    @Test
    void fetchOrdersQueriesOnlyReadyForProcessingWithNewFulfillment() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms"), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(paidForm("o-1")), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        org.mockito.Mockito.verify(restApi).fetchWithAuthRetry(eq("/order/checkout-forms"),
                eq(Map.of("status", "READY_FOR_PROCESSING", "fulfillment.status", "NEW",
                        "limit", "100", "offset", "0")),
                eq(AllegroCheckoutFormsResponse.class));
    }

    @Test
    void fetchOrdersSkipsPrepaymentOrderWithoutFinishedPayment() {
        // given
        AllegroCheckoutForm unpaid = new AllegroCheckoutForm("o-2", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-2", "ONLINE", null),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(), paidForm("x").invoice(), paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(paidForm("o-1"), unpaid), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("o-1", orders.get(0).externalOrderId());
    }

    @Test
    void fetchOrdersImportsCashOnDeliveryWithoutFinishedPayment() {
        // given
        AllegroCheckoutForm cod = new AllegroCheckoutForm("o-3", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-3", "CASH_ON_DELIVERY", null),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(), paidForm("x").invoice(), paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(cod), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("CashOnDelivery", orders.get(0).paymentType());
    }

    @Test
    void fetchOrdersPaginatesUntilTotalCount() {
        // given
        List<AllegroCheckoutForm> firstPage = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> paidForm("o-" + i)).toList();
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms"),
                argThat(m -> m != null && "0".equals(m.get("offset"))), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(firstPage, 100, 101));
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms"),
                argThat(m -> m != null && "100".equals(m.get("offset"))), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(paidForm("o-100")), 1, 101));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(101, orders.size());
    }

    @Test
    void fetchOrdersMapsCompanyCustomerFromInvoice() {
        // given
        AllegroCheckoutForm company = new AllegroCheckoutForm("o-4", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-4", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                new AllegroCheckoutForm.Invoice(true, new AllegroCheckoutForm.InvoiceAddress(
                        "Biurowa 2", "Kraków", "30-001", "PL",
                        new AllegroCheckoutForm.Company("ACME Sp. z o.o.", "6762459846"), null)),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(company), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        MarketplaceOrder order = ordersImport.fetchOrders().get(0);

        // then
        assertEquals(MarketplaceCustomer.CustomerType.COMPANY, order.customer().customerType());
        assertEquals("ACME Sp. z o.o.", order.customer().company());
        assertEquals("6762459846", order.customer().taxId());
        assertEquals("Biurowa 2", order.customer().billingAddress().street());
    }

    @Test
    void fetchOrdersMapsProductsShippingAndPayment() {
        // given
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(paidForm("o-1")), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        MarketplaceOrder order = ordersImport.fetchOrders().get(0);

        // then
        assertEquals("o-1", order.externalOrderId());
        assertEquals(1, order.products().size());
        assertEquals("Laptop X", order.products().get(0).name());
        assertEquals("SKU-1", order.products().get(0).manufacturerCode());
        assertEquals(new BigDecimal("2500.00"), order.products().get(0).priceGross());
        assertEquals(2, order.products().get(0).quantity());
        assertEquals(BigDecimal.ZERO, order.products().get(0).commission());
        assertEquals(new BigDecimal("15.99"), order.shippingCost());
        assertEquals("OnlinePayment", order.paymentType());
        assertEquals("pay-1", order.paymentTransactionId());
        assertEquals(MarketplaceCustomer.CustomerType.INDIVIDUAL, order.customer().customerType());
        assertEquals("Jan Kowalski", order.customer().name());
    }

    @Test
    void mapsPickupPointDeliveryIntoShippingAddress() {
        // given
        AllegroCheckoutForm pickup = new AllegroCheckoutForm("o-6", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-6", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                new AllegroCheckoutForm.Delivery(
                        paidForm("x").delivery().address(),
                        paidForm("x").delivery().cost(),
                        new AllegroCheckoutForm.PickupPoint("ALP123", "Paczkomat ALP123",
                                new AllegroCheckoutForm.PickupPointAddress("Prosta 1", "00-001", "Warszawa"))),
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(pickup), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        MarketplaceCustomer.Address shipping = orders.get(0).customer().shippingAddress();
        assertEquals("ALP123 — Paczkomat ALP123, Prosta 1", shipping.street());
        assertEquals("00-001", shipping.postalCode());
        assertEquals("Warszawa", shipping.city());
    }

    @Test
    void fetchOrdersFallsBackToOfferIdWhenExternalIdMissing() {
        // given
        AllegroCheckoutForm noSku = new AllegroCheckoutForm("o-5", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-5", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(), paidForm("x").invoice(),
                List.of(new AllegroCheckoutForm.LineItem(
                        new AllegroCheckoutForm.Offer("offer-9", "Mysz Y", null),
                        1, new AllegroCheckoutForm.Price("99.00", "PLN"))));
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(noSku), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when / then
        assertEquals("offer-9", ordersImport.fetchOrders().get(0).products().get(0).manufacturerCode());
    }
}
