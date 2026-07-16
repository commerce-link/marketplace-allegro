package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllegroOfferWireFormatTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesOffersPageWithUnknownFieldsIgnored() throws Exception {
        // given
        String json = """
                {
                  "offers": [
                    {
                      "id": "7654321",
                      "name": "Produkt testowy",
                      "external": {"id": "PIM-123"},
                      "sellingMode": {"format": "BUY_NOW", "price": {"amount": "149.00", "currency": "PLN"}},
                      "stock": {"available": 10, "sold": 2},
                      "publication": {"status": "ACTIVE"},
                      "category": {"id": "257150"}
                    },
                    {
                      "id": "7654322",
                      "sellingMode": {"price": {"amount": "10.00", "currency": "PLN"}},
                      "stock": {"available": 0},
                      "publication": {"status": "ENDED"}
                    }
                  ],
                  "count": 2,
                  "totalCount": 2
                }
                """;

        // when
        AllegroOffersResponse response = objectMapper.readValue(json, AllegroOffersResponse.class);

        // then
        assertEquals(2, response.totalCount());
        AllegroOffersResponse.OfferSummary first = response.offers().get(0);
        assertEquals("7654321", first.id());
        assertEquals("PIM-123", first.external().id());
        assertEquals("149.00", first.sellingMode().price().amount());
        assertEquals(10L, first.stock().available());
        assertEquals("ACTIVE", first.publication().status());
        assertNull(response.offers().get(1).external());
    }

    @Test
    void deserializesShippingRatesList() throws Exception {
        // given
        String json = """
                {
                  "shippingRates": [
                    {"id": "2ab35f4c-c54e-4308-a433-c7b4f7086e54", "name": "Cennik testowy CL"},
                    {"id": "9cd12e0a-1111-2222-3333-444455556666", "name": "Drugi cennik"}
                  ]
                }
                """;

        // when
        AllegroShippingRatesResponse response = objectMapper.readValue(json, AllegroShippingRatesResponse.class);

        // then
        assertEquals(2, response.shippingRates().size());
        assertEquals("2ab35f4c-c54e-4308-a433-c7b4f7086e54", response.shippingRates().get(0).id());
        assertEquals("Cennik testowy CL", response.shippingRates().get(0).name());
    }
}
