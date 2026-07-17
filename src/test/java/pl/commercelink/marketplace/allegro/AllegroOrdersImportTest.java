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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllegroOrdersImportTest {

    @Mock
    private RestApiWithRetry restApi;

    private AllegroCheckoutForm paidForm(String id) {
        return new AllegroCheckoutForm(
                id,
                "READY_FOR_PROCESSING",
                new AllegroCheckoutForm.Buyer("b-1", "buyer+abc@user.allegromail.pl", "buyer1", "Jan", "Kowalski", null, "+48123123123"),
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
                        new AllegroCheckoutForm.Company("ACME Sp. z o.o.", "6762459846", null), null)),
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
        MarketplaceCustomer.Address billing = order.customer().billingAddress();
        assertEquals("ACME Sp. z o.o.", billing.name());
        assertEquals("+48123123123", billing.phone());
        assertEquals("Biurowa 2", billing.street());
        assertEquals("30-001", billing.postalCode());
        assertEquals("Kraków", billing.city());
        assertEquals("PL", billing.country());
    }

    @Test
    void individualBillingAddressFullFieldsFromPlainDeliveryAddress() {
        // given: INDIVIDUAL buyer, no invoice, no pickup point -> billing = plain delivery address
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(paidForm("o-22")), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        MarketplaceCustomer.Address billing = ordersImport.fetchOrders().get(0).customer().billingAddress();

        // then
        assertEquals("Jan Kowalski", billing.name());
        assertEquals("+48123123123", billing.phone());
        assertEquals("Prosta 1", billing.street());
        assertEquals("00-001", billing.postalCode());
        assertEquals("Warszawa", billing.city());
        assertEquals("PL", billing.country());
    }

    @Test
    void companyIdsWithPlNipTakesPrecedenceOverLegacyTaxId() {
        // given
        AllegroCheckoutForm company = new AllegroCheckoutForm("o-19", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-19", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                new AllegroCheckoutForm.Invoice(true, new AllegroCheckoutForm.InvoiceAddress(
                        "Biurowa 2", "Kraków", "30-001", "PL",
                        new AllegroCheckoutForm.Company("ACME Sp. z o.o.", null,
                                List.of(new AllegroCheckoutForm.CompanyId("PL_NIP", "5252530705"))),
                        null)),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(company), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when / then
        assertEquals("5252530705", ordersImport.fetchOrders().get(0).customer().taxId());
    }

    @Test
    void companyLegacyTaxIdIsUsedWhenNoIdsPresent() {
        // given
        AllegroCheckoutForm company = new AllegroCheckoutForm("o-20", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-20", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                new AllegroCheckoutForm.Invoice(true, new AllegroCheckoutForm.InvoiceAddress(
                        "Biurowa 2", "Kraków", "30-001", "PL",
                        new AllegroCheckoutForm.Company("ACME Sp. z o.o.", "1112223344", null), null)),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(company), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when / then
        assertEquals("1112223344", ordersImport.fetchOrders().get(0).customer().taxId());
    }

    @Test
    void companyFallsBackToFirstAvailableIdWhenNoPlNipOrLegacyTaxId() {
        // given
        AllegroCheckoutForm company = new AllegroCheckoutForm("o-21", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-21", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                new AllegroCheckoutForm.Invoice(true, new AllegroCheckoutForm.InvoiceAddress(
                        "Biurowa 2", "Kraków", "30-001", "PL",
                        new AllegroCheckoutForm.Company("ACME Sp. z o.o.", null,
                                List.of(new AllegroCheckoutForm.CompanyId("VAT_EU", "DE811907980"))),
                        null)),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(company), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when / then
        assertEquals("DE811907980", ordersImport.fetchOrders().get(0).customer().taxId());
    }

    @Test
    void fetchOrdersMapsProductsShippingAndPayment() {
        // given: buyer email/phone are DISTINCT from each other and from the delivery-address
        // phone so a positional swap (buyer-email/phone, buyer-phone/address-phone) fails the test
        AllegroCheckoutForm.Buyer distinctBuyer = new AllegroCheckoutForm.Buyer(
                "b-1", "buyer+42@user.allegromail.pl", "buyer1", "Jan", "Kowalski", null, "+48555666777");
        AllegroCheckoutForm form = new AllegroCheckoutForm("o-1", "READY_FOR_PROCESSING",
                distinctBuyer,
                paidForm("x").payment(),
                paidForm("x").fulfillment(),
                paidForm("x").delivery(),
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(form), 1, 1));
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
        assertEquals("buyer+42@user.allegromail.pl", order.customer().email());
        assertEquals("+48555666777", order.customer().phone());
        assertNull(order.customer().shippingAddress().pickupPoint());
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
        assertEquals("Prosta 1", shipping.street());
        assertEquals("00-001", shipping.postalCode());
        assertEquals("Warszawa", shipping.city());
        assertEquals("ALP123", shipping.pickupPoint().id());
        assertEquals("Paczkomat ALP123", shipping.pickupPoint().name());
    }

    @Test
    void individualBillingAddressIsHomeAddressNotPickupPoint() {
        // given: pickup-point order (delivery.address = home, delivery.pickupPoint set), no invoice
        AllegroCheckoutForm pickup = new AllegroCheckoutForm("o-13", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-13", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                new AllegroCheckoutForm.Delivery(
                        new AllegroCheckoutForm.DeliveryAddress("Jan", "Kowalski", null, "Domowa 5",
                                "Warszawa", "00-002", "PL", "+48123123123"),
                        paidForm("x").delivery().cost(),
                        new AllegroCheckoutForm.PickupPoint("ALP123", "Paczkomat ALP123",
                                new AllegroCheckoutForm.PickupPointAddress("Prosta 1", "00-001", "Warszawa"))),
                new AllegroCheckoutForm.Invoice(false, null),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(pickup), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        MarketplaceCustomer customer = orders.get(0).customer();
        assertEquals("Domowa 5", customer.billingAddress().street());
        assertNull(customer.billingAddress().pickupPoint());
        assertEquals("Prosta 1", customer.shippingAddress().street());
    }

    @Test
    void individualInvoiceWithNaturalPersonBecomesBillingAddress() {
        // given: invoice.required=true, invoice.address {street/zip/city/country, naturalPerson{firstName,lastName}}, no company
        AllegroCheckoutForm form = new AllegroCheckoutForm("o-14", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-14", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                new AllegroCheckoutForm.Invoice(true, new AllegroCheckoutForm.InvoiceAddress(
                        "Fakturowa 3", "Kraków", "30-002", "PL",
                        null, new AllegroCheckoutForm.NaturalPerson("Jan", "Iksinski"))),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(form), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        MarketplaceOrder order = ordersImport.fetchOrders().get(0);

        // then
        MarketplaceCustomer customer = order.customer();
        assertEquals(MarketplaceCustomer.CustomerType.INDIVIDUAL, customer.customerType());
        assertEquals("Fakturowa 3", customer.billingAddress().street());
        assertEquals("30-002", customer.billingAddress().postalCode());
        assertEquals("Kraków", customer.billingAddress().city());
        assertEquals("Jan Iksinski", customer.billingAddress().name());
    }

    @Test
    void pickupPointWithoutOwnAddressFallsBackToDeliveryAddressFields() {
        // given: delivery.pickupPoint {id,name, address:null}
        AllegroCheckoutForm pickup = new AllegroCheckoutForm("o-15", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-15", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                new AllegroCheckoutForm.Delivery(
                        paidForm("x").delivery().address(),
                        paidForm("x").delivery().cost(),
                        new AllegroCheckoutForm.PickupPoint("ALP999", "Paczkomat ALP999", null)),
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(pickup), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        MarketplaceCustomer.Address shipping = orders.get(0).customer().shippingAddress();
        assertEquals("Prosta 1", shipping.street());
        assertEquals("00-001", shipping.postalCode());
        assertEquals("Warszawa", shipping.city());
        assertEquals("ALP999", shipping.pickupPoint().id());
    }

    @Test
    void skipsFormWithMissingCost() {
        // given: delivery present, cost null -> skipped, valid sibling imported
        AllegroCheckoutForm missingCost = new AllegroCheckoutForm("o-16", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-16", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                new AllegroCheckoutForm.Delivery(paidForm("x").delivery().address(), null, null),
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(missingCost, paidForm("o-1")), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("o-1", orders.get(0).externalOrderId());
    }

    @Test
    void skipsFormWithMissingAddress() {
        // given: delivery present, address null -> skipped
        AllegroCheckoutForm missingAddress = new AllegroCheckoutForm("o-17", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-17", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                new AllegroCheckoutForm.Delivery(null, paidForm("x").delivery().cost(), null),
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(missingAddress, paidForm("o-1")), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("o-1", orders.get(0).externalOrderId());
    }

    @Test
    void skipsFormWithEmptyLineItems() {
        // given: lineItems [] -> skipped
        AllegroCheckoutForm emptyLineItems = new AllegroCheckoutForm("o-18", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-18", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                paidForm("x").invoice(),
                List.of());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(emptyLineItems, paidForm("o-1")), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("o-1", orders.get(0).externalOrderId());
    }

    @Test
    void skipsFormWithMissingDeliveryInsteadOfFailingWholeImport() {
        // given
        AllegroCheckoutForm missingDelivery = new AllegroCheckoutForm("o-7", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-7", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                null,
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(missingDelivery, paidForm("o-1")), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("o-1", orders.get(0).externalOrderId());
    }

    @Test
    void skipsFormWithMissingBuyerInsteadOfFailingWholeImport() {
        // given
        AllegroCheckoutForm missingBuyer = new AllegroCheckoutForm("o-23", "READY_FOR_PROCESSING",
                null,
                new AllegroCheckoutForm.Payment("pay-23", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(missingBuyer, paidForm("o-1")), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("o-1", orders.get(0).externalOrderId());
    }

    @Test
    void skipsFormWithLineItemMissingPrice() {
        // given
        AllegroCheckoutForm missingPrice = new AllegroCheckoutForm("o-24", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-24", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                paidForm("x").invoice(),
                List.of(new AllegroCheckoutForm.LineItem(
                        new AllegroCheckoutForm.Offer("offer-1", "Laptop X", new AllegroCheckoutForm.External("SKU-1")),
                        2,
                        null)));
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(missingPrice, paidForm("o-1")), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        assertEquals("o-1", orders.get(0).externalOrderId());
    }

    @Test
    void companyBuyerWithoutInvoiceGetsCompanyNameInsteadOfNullNull() {
        // given
        AllegroCheckoutForm.Buyer companyBuyer = new AllegroCheckoutForm.Buyer(
                "b-2", "buyer2@user.allegromail.pl", "buyer2", null, null, "ACME Sp. z o.o.", "+48123123123");
        AllegroCheckoutForm form = new AllegroCheckoutForm("o-8", "READY_FOR_PROCESSING",
                companyBuyer,
                new AllegroCheckoutForm.Payment("pay-8", "ONLINE", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                new AllegroCheckoutForm.Invoice(false, null),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(form), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals("ACME Sp. z o.o.", orders.get(0).customer().name());
    }

    @Test
    void mapsNullPaymentTypeToBankTransfer() {
        // given
        AllegroCheckoutForm form = new AllegroCheckoutForm("o-9", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-9", null, "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(),
                paidForm("x").invoice(),
                paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(form), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when / then
        assertEquals("BankTransfer", ordersImport.fetchOrders().get(0).paymentType());
    }

    @Test
    void stopsPaginationWhenPageIsEmptyDespiteTotalCount() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms"),
                argThat(m -> m != null && "0".equals(m.get("offset"))), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(paidForm("o-1")), 1, 500));
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms"),
                argThat(m -> m != null && "1".equals(m.get("offset"))), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(), 0, 500));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals(1, orders.size());
        verify(restApi, times(2))
                .fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class));
    }

    @Test
    void mapsWireTransferAndExtendedTermToBankTransfer() {
        // given
        AllegroCheckoutForm wireTransfer = new AllegroCheckoutForm("o-10", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-10", "WIRE_TRANSFER", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(), paidForm("x").invoice(), paidForm("x").lineItems());
        AllegroCheckoutForm extendedTerm = new AllegroCheckoutForm("o-11", "READY_FOR_PROCESSING",
                paidForm("x").buyer(),
                new AllegroCheckoutForm.Payment("pay-11", "EXTENDED_TERM", "2026-07-10T10:00:00Z"),
                new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(), paidForm("x").invoice(), paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(wireTransfer, extendedTerm), 2, 2));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when
        List<MarketplaceOrder> orders = ordersImport.fetchOrders();

        // then
        assertEquals("BankTransfer", orders.get(0).paymentType());
        assertEquals("BankTransfer", orders.get(1).paymentType());
    }

    @Test
    void filtersOutFormWithoutPayment() {
        // given
        AllegroCheckoutForm noPayment = new AllegroCheckoutForm("o-12", "READY_FOR_PROCESSING",
                paidForm("x").buyer(), null, new AllegroCheckoutForm.Fulfillment("NEW"),
                paidForm("x").delivery(), paidForm("x").invoice(), paidForm("x").lineItems());
        when(restApi.fetchWithAuthRetry(anyString(), anyMap(), eq(AllegroCheckoutFormsResponse.class)))
                .thenReturn(new AllegroCheckoutFormsResponse(List.of(noPayment), 1, 1));
        AllegroOrdersImport ordersImport = new AllegroOrdersImport(restApi);

        // when / then
        assertEquals(0, ordersImport.fetchOrders().size());
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
