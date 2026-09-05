package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllegroReturnsWireFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesRealisticCustomerReturnsPage() throws Exception {
        // given: shape from GET /order/customer-returns (beta), unknown fields at every level
        String json = """
                {
                  "count": 1,
                  "customerReturns": [{
                    "id": "8228462e-dc5f-4cae-80f6-88b320b2565a",
                    "isFulfillment": false,
                    "createdAt": "2026-08-20T12:13:53.464Z",
                    "referenceNumber": "XGQX/2026",
                    "orderId": "4a0a6511-dfd7-11eb-b6c4-d37454079c02",
                    "buyer": {"email": "xp8f9zypt5@allegro.pl", "login": "Client:44300636"},
                    "items": [{
                      "offerId": "7680560740",
                      "quantity": 2,
                      "name": "Apple iPhone 12 Pro",
                      "price": {"amount": "1234.00", "currency": "PLN"},
                      "url": "https://allegro.pl/oferta/7680560740",
                      "reason": {"type": "NOT_AS_DESCRIBED", "userComment": "Wrong colour"},
                      "serialNumbers": ["SN-1"]
                    }],
                    "refund": {"bankAccount": {"iban": "PL90"}},
                    "parcels": [{
                      "createdAt": "2026-08-20T12:13:59.001Z",
                      "waybill": "0000123456",
                      "carrierId": "INPOST",
                      "transportingWaybill": null,
                      "transportingCarrierId": null,
                      "sender": {"phoneNumber": "+48600600600"}
                    }],
                    "rejection": null,
                    "marketplaceId": "allegro-pl",
                    "status": "IN_TRANSIT"
                  }]
                }
                """;

        // when
        AllegroCustomerReturnsResponse page = objectMapper.readValue(json, AllegroCustomerReturnsResponse.class);

        // then
        assertEquals(1, page.count());
        AllegroCustomerReturn ret = page.customerReturns().get(0);
        assertEquals("8228462e-dc5f-4cae-80f6-88b320b2565a", ret.id());
        assertFalse(ret.isFulfillment());
        assertEquals("XGQX/2026", ret.referenceNumber());
        assertEquals("4a0a6511-dfd7-11eb-b6c4-d37454079c02", ret.orderId());
        assertEquals("IN_TRANSIT", ret.status());
        assertEquals("2026-08-20T12:13:53.464Z", ret.createdAt());
        assertEquals("7680560740", ret.items().get(0).offerId());
        assertEquals(2, ret.items().get(0).quantity());
        assertEquals("1234.00", ret.items().get(0).price().amount());
        assertEquals("NOT_AS_DESCRIBED", ret.items().get(0).reason().type());
        assertEquals("Wrong colour", ret.items().get(0).reason().userComment());
        assertEquals("0000123456", ret.parcels().get(0).waybill());
        assertEquals("INPOST", ret.parcels().get(0).carrierId());
        assertNull(ret.rejection());
    }

    @Test
    void deserializesRejectedReturn() throws Exception {
        // given
        String json = """
                {"id": "r-1", "orderId": "o-1", "status": "REJECTED", "items": [],
                 "rejection": {"code": "REFUND_REJECTED", "reason": "Damaged", "createdAt": "2026-08-21T10:00:00Z"}}
                """;

        // when
        AllegroCustomerReturn ret = objectMapper.readValue(json, AllegroCustomerReturn.class);

        // then
        assertEquals("REFUND_REJECTED", ret.rejection().code());
        assertEquals("Damaged", ret.rejection().reason());
        assertTrue(ret.parcels() == null || ret.parcels().isEmpty());
    }

    @Test
    void serializesRefundRequestWithDeliveryAndOmitsNulls() throws Exception {
        // given
        AllegroRefundRequest request = new AllegroRefundRequest(
                new AllegroRefundRequest.Ref("pay-1"),
                new AllegroRefundRequest.Ref("order-1"),
                "cmd-1",
                "REFUND",
                List.of(new AllegroRefundRequest.LineItem("li-1", "QUANTITY", 2)),
                null,
                new AllegroRefundRequest.Delivery(new AllegroRefundRequest.Money("12.99", "PLN")),
                "Return XGQX/2026");

        // when
        String json = objectMapper.writeValueAsString(request);

        // then
        assertEquals("{\"payment\":{\"id\":\"pay-1\"},\"order\":{\"id\":\"order-1\"},\"commandId\":\"cmd-1\","
                + "\"reason\":\"REFUND\",\"lineItems\":[{\"id\":\"li-1\",\"type\":\"QUANTITY\",\"quantity\":2}],"
                + "\"delivery\":{\"value\":{\"amount\":\"12.99\",\"currency\":\"PLN\"}},"
                + "\"sellerComment\":\"Return XGQX/2026\"}", json);
    }

    @Test
    void serializesRefundWithDepositsForDepositBearingLineItems() throws Exception {
        // given / when
        String json = objectMapper.writeValueAsString(refundRequestWithDeposit());

        // then
        assertTrue(json.contains("\"deposits\":[{\"lineItemId\":\"li-1\",\"totalValue\":{\"amount\":\"1.00\",\"currency\":\"PLN\"}}]"));
    }

    private static AllegroRefundRequest refundRequestWithDeposit() {
        return new AllegroRefundRequest(
                new AllegroRefundRequest.Ref("pay-1"),
                new AllegroRefundRequest.Ref("order-1"),
                "cmd-1",
                "REFUND",
                List.of(new AllegroRefundRequest.LineItem("li-1", "QUANTITY", 1)),
                List.of(new AllegroRefundRequest.Deposit("li-1", new AllegroRefundRequest.Money("1.00", "PLN"))),
                null,
                "Return XGQX/2026");
    }

    @Test
    void serializesRefundRequestWithoutDelivery() throws Exception {
        // given
        AllegroRefundRequest request = new AllegroRefundRequest(
                new AllegroRefundRequest.Ref("pay-1"), new AllegroRefundRequest.Ref("order-1"), "cmd-1", "REFUND",
                List.of(new AllegroRefundRequest.LineItem("li-1", "QUANTITY", 1)), null, null, null);

        // when
        String json = objectMapper.writeValueAsString(request);

        // then
        assertFalse(json.contains("delivery"));
        assertFalse(json.contains("sellerComment"));
    }

    @Test
    void serializesRejectionRequest() throws Exception {
        // given
        AllegroReturnRejectionRequest request = AllegroReturnRejectionRequest.refundRejected("Item damaged by buyer");

        // when
        String json = objectMapper.writeValueAsString(request);

        // then
        assertEquals("{\"rejection\":{\"code\":\"REFUND_REJECTED\",\"reason\":\"Item damaged by buyer\"}}", json);
    }

    @Test
    void deserializesLineItemIdFromCheckoutForm() throws Exception {
        // given
        String json = """
                {"id": "cf-1", "lineItems": [{"id": "li-1", "offer": {"id": "7680560740", "name": "X",
                 "external": {"id": "SKU-1"}}, "quantity": 1, "price": {"amount": "10.00", "currency": "PLN"}}]}
                """;

        // when
        AllegroCheckoutForm form = objectMapper.readValue(json, AllegroCheckoutForm.class);

        // then
        assertEquals("li-1", form.lineItems().get(0).id());
        assertEquals("SKU-1", form.lineItems().get(0).offer().external().id());
    }
}
