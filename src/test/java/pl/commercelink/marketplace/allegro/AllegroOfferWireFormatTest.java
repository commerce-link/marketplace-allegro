package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.api.MarketplaceOffer;

import java.util.List;

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

    @Test
    void serializesCreateOfferWithProductGpsrImagesAndParameters() throws Exception {
        // given
        MarketplaceOffer offer = new MarketplaceOffer(
                "PIM-123", "5901234567890", "MC-1", "Acme", "Produkt testowy", "Laptopy", 149L, 10L, 3);

        // when
        String json = objectMapper.writeValueAsString(AllegroOfferRequest.createOffer(
                offer, "rate-1", "rp-1", "prod-uuid",
                List.of("https://img.example/1.jpg"),
                List.of(new AllegroOfferRequest.OfferParameter("224017", List.of("MC-1")),
                        new AllegroOfferRequest.OfferParameter("237206", List.of("MC-1")))));

        // then
        assertEquals("""
                {"productSet":[{"product":{"id":"prod-uuid"},\
                "responsibleProducer":{"id":"rp-1"},\
                "safetyInformation":{"type":"TEXT","description":"Szczegółowe informacje o bezpieczeństwie produktu dostępne są u producenta."}}],\
                "external":{"id":"PIM-123"},\
                "sellingMode":{"price":{"amount":"149.00","currency":"PLN"}},\
                "stock":{"available":10,"unit":"UNIT"},\
                "delivery":{"shippingRates":{"id":"rate-1"}},\
                "publication":{"status":"ACTIVE"},\
                "images":["https://img.example/1.jpg"],\
                "parameters":[{"id":"224017","values":["MC-1"]},{"id":"237206","values":["MC-1"]}]}""", json);
    }

    @Test
    void createOfferOmitsParametersWhenNoneMissing() throws Exception {
        // given
        MarketplaceOffer offer = new MarketplaceOffer(
                "PIM-123", "5901234567890", "MC-1", null, null, null, 149L, 10L, 3);

        // when
        String json = objectMapper.writeValueAsString(AllegroOfferRequest.createOffer(
                offer, "rate-1", "rp-1", "prod-uuid", List.of("https://img.example/1.jpg"), List.of()));

        // then
        assertFalse(json.contains("\"parameters\""));
    }

    @Test
    void deserializesProductsSearchPage() throws Exception {
        // given
        String json = """
                {
                  "products": [
                    {
                      "id": "prod-uuid",
                      "name": "Produkt",
                      "parameters": [{"id": "224017", "name": "Kod producenta", "values": ["AK-1"]}],
                      "images": [{"url": "https://img.example/1.jpg"}],
                      "productSafety": {"responsibleProducers": [{"id": "rp-akyga", "name": "Akyga."}]}
                    }
                  ]
                }
                """;

        // when
        AllegroProductsResponse response = objectMapper.readValue(json, AllegroProductsResponse.class);

        // then
        AllegroProductsResponse.CatalogProduct product = response.products().get(0);
        assertEquals("prod-uuid", product.id());
        assertEquals("224017", product.parameters().get(0).id());
        assertEquals("https://img.example/1.jpg", product.images().get(0).url());
        assertEquals("rp-akyga", product.productSafety().responsibleProducers().get(0).id());
    }

    @Test
    void deserializesResponsibleProducersList() throws Exception {
        // given
        String json = """
                {"responsibleProducers": [{"id": "rp-1", "name": "Producent Sp. z o.o."}], "count": 1, "totalCount": 1}
                """;

        // when
        AllegroResponsibleProducersResponse response =
                objectMapper.readValue(json, AllegroResponsibleProducersResponse.class);

        // then
        assertEquals("rp-1", response.responsibleProducers().get(0).id());
    }

    @Test
    void serializesUpdateOfferWithoutProductSetAndDelivery() throws Exception {
        // given
        MarketplaceOffer offer = new MarketplaceOffer(
                "PIM-123", "5901234567890", null, null, null, null, 99L, 5L, 3);

        // when
        String json = objectMapper.writeValueAsString(AllegroOfferRequest.updateOffer(offer));

        // then
        assertEquals("""
                {"sellingMode":{"price":{"amount":"99.00","currency":"PLN"}},\
                "stock":{"available":5,"unit":"UNIT"},\
                "publication":{"status":"ACTIVE"}}""", json);
    }

    @Test
    void updateOfferWithZeroQuantityEndsPublication() throws Exception {
        // given
        MarketplaceOffer offer = new MarketplaceOffer(
                "PIM-123", null, null, null, null, null, 99L, 0L, 0);

        // when
        String json = objectMapper.writeValueAsString(AllegroOfferRequest.updateOffer(offer));

        // then
        assertEquals("""
                {"sellingMode":{"price":{"amount":"99.00","currency":"PLN"}},\
                "stock":{"available":0,"unit":"UNIT"},\
                "publication":{"status":"ENDED"}}""", json);
    }

    @Test
    void serializesEndOfferAsPublicationOnly() throws Exception {
        // when
        String json = objectMapper.writeValueAsString(AllegroOfferRequest.endOffer());

        // then
        assertEquals("{\"publication\":{\"status\":\"ENDED\"}}", json);
    }
}
