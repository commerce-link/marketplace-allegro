package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllegroWireFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesRealisticCheckoutForm() throws Exception {
        // given: a realistic Allegro checkout-forms payload with unknown fields sprinkled
        // at every nesting level, to lock both the field-name mapping and ignoreUnknown behavior
        String json = """
                {
                  "id": "checkout-form-1",
                  "status": "READY_FOR_PROCESSING",
                  "summary": {"totalCount": 999},
                  "updatedAt": "2026-07-10T10:05:00Z",
                  "revision": "abc123",
                  "buyer": {
                    "id": "b-1",
                    "email": "buyer+abc@user.allegromail.pl",
                    "login": "buyer1",
                    "firstName": "Jan",
                    "lastName": "Kowalski",
                    "companyName": "ACME Sp. z o.o.",
                    "phoneNumber": "+48123123123",
                    "guest": false
                  },
                  "payment": {
                    "id": "pay-1",
                    "type": "ONLINE",
                    "finishedAt": "2026-07-10T10:00:00Z",
                    "provider": "PayU"
                  },
                  "fulfillment": {
                    "status": "NEW",
                    "shipmentSummary": {"lastShipmentDate": null}
                  },
                  "delivery": {
                    "address": {
                      "firstName": "Jan",
                      "lastName": "Kowalski",
                      "companyName": null,
                      "street": "Prosta 1",
                      "city": "Warszawa",
                      "zipCode": "00-001",
                      "countryCode": "PL",
                      "phoneNumber": "+48123123123"
                    },
                    "cost": {"amount": "15.99", "currency": "PLN"},
                    "method": {"id": "method-1", "name": "Paczkomaty InPost"},
                    "pickupPoint": {
                      "id": "ALP123",
                      "name": "Paczkomat ALP123",
                      "description": "Paczkomat przy sklepie",
                      "address": {"street": "Prosta 1", "zipCode": "00-001", "city": "Warszawa"}
                    }
                  },
                  "invoice": {
                    "required": true,
                    "address": {
                      "street": "Biurowa 2",
                      "city": "Krakow",
                      "zipCode": "30-001",
                      "countryCode": "PL",
                      "company": {
                        "name": "ACME Sp. z o.o.",
                        "taxId": "6762459846",
                        "ids": [{"type": "PL_NIP", "value": "5252530705"}]
                      },
                      "naturalPerson": null
                    }
                  },
                  "lineItems": [
                    {
                      "offer": {
                        "id": "offer-1",
                        "name": "Laptop X",
                        "external": {"id": "SKU-1"},
                        "unit": "ITEM"
                      },
                      "quantity": 2,
                      "price": {"amount": "2500.00", "currency": "PLN"},
                      "originalPrice": {"amount": "2600.00", "currency": "PLN"}
                    }
                  ]
                }
                """;

        // when
        AllegroCheckoutForm form = objectMapper.readValue(json, AllegroCheckoutForm.class);

        // then
        assertEquals("checkout-form-1", form.id());
        assertEquals("READY_FOR_PROCESSING", form.status());

        assertEquals("b-1", form.buyer().id());
        assertEquals("buyer+abc@user.allegromail.pl", form.buyer().email());
        assertEquals("buyer1", form.buyer().login());
        assertEquals("Jan", form.buyer().firstName());
        assertEquals("Kowalski", form.buyer().lastName());
        assertEquals("ACME Sp. z o.o.", form.buyer().companyName());
        assertEquals("+48123123123", form.buyer().phoneNumber());

        assertEquals("pay-1", form.payment().id());
        assertEquals("ONLINE", form.payment().type());
        assertEquals("2026-07-10T10:00:00Z", form.payment().finishedAt());

        assertEquals("NEW", form.fulfillment().status());

        AllegroCheckoutForm.DeliveryAddress address = form.delivery().address();
        assertEquals("Jan", address.firstName());
        assertEquals("Kowalski", address.lastName());
        assertNull(address.companyName());
        assertEquals("Prosta 1", address.street());
        assertEquals("Warszawa", address.city());
        assertEquals("00-001", address.zipCode());
        assertEquals("PL", address.countryCode());
        assertEquals("+48123123123", address.phoneNumber());

        assertEquals("15.99", form.delivery().cost().amount());
        assertEquals("PLN", form.delivery().cost().currency());

        AllegroCheckoutForm.PickupPoint pickupPoint = form.delivery().pickupPoint();
        assertEquals("ALP123", pickupPoint.id());
        assertEquals("Paczkomat ALP123", pickupPoint.name());
        assertEquals("Prosta 1", pickupPoint.address().street());
        assertEquals("00-001", pickupPoint.address().zipCode());
        assertEquals("Warszawa", pickupPoint.address().city());

        assertTrue(form.invoice().required());
        AllegroCheckoutForm.InvoiceAddress invoiceAddress = form.invoice().address();
        assertEquals("Biurowa 2", invoiceAddress.street());
        assertEquals("Krakow", invoiceAddress.city());
        assertEquals("30-001", invoiceAddress.zipCode());
        assertEquals("PL", invoiceAddress.countryCode());
        assertNull(invoiceAddress.naturalPerson());

        AllegroCheckoutForm.Company company = invoiceAddress.company();
        assertEquals("ACME Sp. z o.o.", company.name());
        assertEquals("6762459846", company.taxId());
        assertEquals(1, company.ids().size());
        assertEquals("PL_NIP", company.ids().get(0).type());
        assertEquals("5252530705", company.ids().get(0).value());

        assertEquals(1, form.lineItems().size());
        AllegroCheckoutForm.LineItem lineItem = form.lineItems().get(0);
        assertEquals("offer-1", lineItem.offer().id());
        assertEquals("Laptop X", lineItem.offer().name());
        assertEquals("SKU-1", lineItem.offer().external().id());
        assertEquals(2, lineItem.quantity());
        assertEquals("2500.00", lineItem.price().amount());
        assertEquals("PLN", lineItem.price().currency());
    }

    @Test
    void serializesShipmentCreateRequest() throws Exception {
        // given
        AllegroOrderLifecycleEventHandler.ShipmentCreateRequest request =
                new AllegroOrderLifecycleEventHandler.ShipmentCreateRequest("DPD", "00000001234567", null);

        // when
        String json = objectMapper.writeValueAsString(request);

        // then
        assertTrue(json.contains("\"carrierId\":\"DPD\""));
        assertTrue(json.contains("\"waybill\":\"00000001234567\""));
    }

    @Test
    void serializesFulfillmentUpdateRequest() throws Exception {
        // given
        AllegroOrderLifecycleEventHandler.FulfillmentUpdateRequest request =
                new AllegroOrderLifecycleEventHandler.FulfillmentUpdateRequest("SENT");

        // when
        String json = objectMapper.writeValueAsString(request);

        // then
        assertEquals("{\"status\":\"SENT\"}", json);
    }
}
