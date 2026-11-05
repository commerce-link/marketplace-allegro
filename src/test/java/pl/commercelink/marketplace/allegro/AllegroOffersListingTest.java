package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceListedOffer;
import pl.commercelink.marketplace.api.MarketplaceOffersPage;
import pl.commercelink.marketplace.api.MarketplaceRateLimitedException;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllegroOffersListingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private RestApiWithRetry restApi;

    private String listedOffer(String id, String externalId, String name, long available, String amount, String status) {
        String external = externalId == null ? "null" : "{\"id\":\"" + externalId + "\"}";
        return """
                {"id":"%s","name":"%s","external":%s,"stock":{"available":%d,"sold":0},
                 "sellingMode":{"format":"BUY_NOW","price":{"amount":"%s","currency":"PLN"}},
                 "publication":{"status":"%s"}}
                """.formatted(id, name, external, available, amount, status);
    }

    private String activeOffer(String id) {
        return listedOffer(id, null, "Oferta " + id, 1, "10.00", "ACTIVE");
    }

    private String endedOffer(String id) {
        return listedOffer(id, null, "Oferta " + id, 0, "10.00", "ENDED");
    }

    private void stubList(Integer count, int totalCount, String... offers) throws Exception {
        String json = "{\"offers\":[" + String.join(",", offers) + "],\"count\":" + count + ",\"totalCount\":" + totalCount + "}";
        when(restApi.fetchWithAuthRetry(eq("/sale/offers"), anyMap(), eq(AllegroListedOffersResponse.class)))
                .thenReturn(objectMapper.readValue(json, AllegroListedOffersResponse.class));
    }

    private void stubListWithoutTotalCount(String... offers) throws Exception {
        String json = "{\"offers\":[" + String.join(",", offers) + "],\"count\":" + offers.length + "}";
        when(restApi.fetchWithAuthRetry(eq("/sale/offers"), anyMap(), eq(AllegroListedOffersResponse.class)))
                .thenReturn(objectMapper.readValue(json, AllegroListedOffersResponse.class));
    }

    private String[] endedOffers(long firstId, int size) {
        String[] ended = new String[size];
        for (int i = 0; i < size; i++) {
            ended[i] = endedOffer(String.valueOf(firstId + i));
        }
        return ended;
    }

    private void stubList(String... offers) throws Exception {
        stubList(offers.length, offers.length, offers);
    }

    private String product(String... eans) {
        String values = eans.length == 0 ? "" : "\"" + String.join("\",\"", eans) + "\"";
        return """
                {"product":{"id":"00000000-0000-0000-0000-000000000001",
                  "parameters":[{"id":"11323","name":"Stan","values":["Nowy"]},
                                {"id":"225693","name":"EAN (GTIN)","values":[%s],"valuesIds":null,"rangeValue":null}]},
                 "quantity":{"value":1}}
                """.formatted(values);
    }

    private void stubDetails(String offerId, String productSetJson) throws Exception {
        String json = "{\"id\":\"" + offerId + "\",\"productSet\":" + productSetJson + ",\"parameters\":[]}";
        when(restApi.fetchWithAuthRetry(eq("/sale/product-offers/" + offerId), anyMap(), eq(AllegroProductOfferResponse.class)))
                .thenReturn(objectMapper.readValue(json, AllegroProductOfferResponse.class));
    }

    private void stubDetails(String offerId) throws Exception {
        stubDetails(offerId, "[" + product("2000000000015") + "]");
    }

    @Test
    void requestsTheFirstPageOfFiftyAtOffsetZero() throws Exception {
        // given
        stubList();

        // when
        new AllegroOffersListing(restApi).page(null);

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(restApi).fetchWithAuthRetry(eq("/sale/offers"), params.capture(), eq(AllegroListedOffersResponse.class));
        assertEquals(Map.of("limit", "50", "offset", "0"), params.getValue());
    }

    @Test
    void mapsAnOfferWithItsEans() throws Exception {
        // given
        stubList(listedOffer("7700000001", "BL-1", "Mysz testowa", 4, "49.99", "ACTIVE"));
        stubDetails("7700000001", "[" + product("2000000000015") + "]");

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertEquals(List.of(new MarketplaceListedOffer(
                "BL-1", List.of("2000000000015"), "Mysz testowa", 4, new BigDecimal("49.99"), true)), page.offers());
        verify(restApi).fetchWithAuthRetry(eq("/sale/product-offers/7700000001"), eq(Map.of()), eq(AllegroProductOfferResponse.class));
    }

    @Test
    void keysAnOfferWithoutSignatureByItsId() throws Exception {
        // given
        stubList(listedOffer("7700000001", null, "Mysz testowa", 4, "49.99", "ACTIVE"));
        stubDetails("7700000001");

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertEquals("7700000001", page.offers().get(0).sellerOfferKey());
    }

    @Test
    void collectsDistinctEansOfAllProductsInOrder() throws Exception {
        // given
        stubList(activeOffer("7700000001"));
        stubDetails("7700000001", "[" + product("2000000000015", "2000000000022") + ","
                + product("2000000000022", "2000000000039") + "]");

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertEquals(List.of("2000000000015", "2000000000022", "2000000000039"), page.offers().get(0).eans());
    }

    @Test
    void returnsNoEansForAnOfferWithoutProductOrParameter() throws Exception {
        // given
        stubList(activeOffer("7700000001"), activeOffer("7700000002"));
        stubDetails("7700000001", "null");
        stubDetails("7700000002", """
                [{"product":{"id":"00000000-0000-0000-0000-000000000002","parameters":[{"id":"11323","values":["Nowy"]}]}}]
                """);

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertEquals(List.of(), page.offers().get(0).eans());
        assertEquals(List.of(), page.offers().get(1).eans());
    }

    @Test
    void marksAnInactiveOfferAsNotActive() throws Exception {
        // given
        stubList(listedOffer("7700000001", "BL-1", "Mysz testowa", 0, "49.99", "INACTIVE"));
        stubDetails("7700000001");

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertFalse(page.offers().get(0).active());
    }

    @Test
    void skipsEndedOffersWithoutRequestingTheirDetails() throws Exception {
        // given
        stubList(endedOffer("7700000001"), activeOffer("7700000002"));
        stubDetails("7700000002");

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertEquals(1, page.offers().size());
        assertEquals("7700000002", page.offers().get(0).sellerOfferKey());
        verify(restApi, never()).fetchWithAuthRetry(eq("/sale/product-offers/7700000001"), anyMap(), any());
    }

    @Test
    void keepsWalkingAfterAPageOfEndedOffers() throws Exception {
        // given
        String[] ended = new String[50];
        for (int i = 0; i < ended.length; i++) {
            ended[i] = endedOffer(String.valueOf(7700000100L + i));
        }
        stubList(50, 120, ended);

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertTrue(page.offers().isEmpty());
        assertEquals("50", page.nextCursor());
        assertEquals(120, page.total());
    }

    @Test
    void endsAtTheTotalCount() throws Exception {
        // given
        String[] ended = new String[20];
        for (int i = 0; i < ended.length; i++) {
            ended[i] = endedOffer(String.valueOf(7700000200L + i));
        }
        stubList(20, 120, ended);

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page("100");

        // then
        assertNull(page.nextCursor());
        assertEquals(120, page.total());
    }

    @Test
    void continuesWithoutTotalCountWhenThePageIsFull() throws Exception {
        // given
        stubListWithoutTotalCount(endedOffers(7700000300L, 50));

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertEquals("50", page.nextCursor());
        assertNull(page.total());
    }

    @Test
    void endsWithoutTotalCountOnAShortPage() throws Exception {
        // given
        stubListWithoutTotalCount(endedOffers(7700000400L, 20));

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertNull(page.nextCursor());
        assertNull(page.total());
    }

    @Test
    void endsOnAnEmptyListPage() throws Exception {
        // given
        stubList(0, 500);

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page("50");

        // then
        assertNull(page.nextCursor());
    }

    @Test
    void rejectsAMalformedCursorBeforeAnyRequest() {
        // given
        AllegroOffersListing listing = new AllegroOffersListing(restApi);

        // when / then
        assertThrows(IllegalArgumentException.class, () -> listing.page("abc"));
        assertThrows(IllegalArgumentException.class, () -> listing.page("-1"));
        verifyNoInteractions(restApi);
    }

    @Test
    void throwsRateLimitedOnAThrottledList() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/sale/offers"), anyMap(), eq(AllegroListedOffersResponse.class)))
                .thenThrow(new HttpClientException(429, "too many requests"));

        // when
        MarketplaceRateLimitedException e = assertThrows(MarketplaceRateLimitedException.class,
                () -> new AllegroOffersListing(restApi).page(null));

        // then
        assertEquals(Duration.ofSeconds(60), e.retryAfter());
    }

    @Test
    void throwsRateLimitedWhenADetailIsThrottledMidPage() throws Exception {
        // given
        stubList(activeOffer("1"), activeOffer("2"), activeOffer("3"));
        stubDetails("1");
        stubDetails("2");
        when(restApi.fetchWithAuthRetry(eq("/sale/product-offers/3"), anyMap(), eq(AllegroProductOfferResponse.class)))
                .thenThrow(new HttpClientException(429, "too many requests"));

        // when
        MarketplaceRateLimitedException e = assertThrows(MarketplaceRateLimitedException.class,
                () -> new AllegroOffersListing(restApi).page(null));

        // then
        assertEquals(Duration.ofSeconds(60), e.retryAfter());
    }

    @Test
    void skipsAnOfferDeletedBeforeItsDetails() throws Exception {
        // given
        stubList(activeOffer("1"), activeOffer("2"), activeOffer("3"));
        stubDetails("1");
        when(restApi.fetchWithAuthRetry(eq("/sale/product-offers/2"), anyMap(), eq(AllegroProductOfferResponse.class)))
                .thenThrow(new HttpClientException(404, "{\"errors\":[{\"code\":\"OfferNotFoundException\"}]}"));
        stubDetails("3");

        // when
        MarketplaceOffersPage page = new AllegroOffersListing(restApi).page(null);

        // then
        assertEquals(List.of("1", "3"), page.offers().stream().map(MarketplaceListedOffer::sellerOfferKey).toList());
    }

    @Test
    void rethrowsOtherHttpErrors() throws Exception {
        // given
        stubList(activeOffer("1"));
        when(restApi.fetchWithAuthRetry(eq("/sale/product-offers/1"), anyMap(), eq(AllegroProductOfferResponse.class)))
                .thenThrow(new HttpClientException(500, "server error"));

        // when
        HttpClientException e = assertThrows(HttpClientException.class,
                () -> new AllegroOffersListing(restApi).page(null));

        // then
        assertEquals(500, e.getStatusCode());
    }

    @Test
    void requestsDetailsOneAfterAnotherInListOrder() throws Exception {
        // given
        stubList(activeOffer("1"), activeOffer("2"), activeOffer("3"));
        stubDetails("1");
        stubDetails("2");
        stubDetails("3");

        // when
        new AllegroOffersListing(restApi).page(null);

        // then
        InOrder inOrder = inOrder(restApi);
        inOrder.verify(restApi).fetchWithAuthRetry(eq("/sale/offers"), anyMap(), eq(AllegroListedOffersResponse.class));
        inOrder.verify(restApi).fetchWithAuthRetry(eq("/sale/product-offers/1"), anyMap(), eq(AllegroProductOfferResponse.class));
        inOrder.verify(restApi).fetchWithAuthRetry(eq("/sale/product-offers/2"), anyMap(), eq(AllegroProductOfferResponse.class));
        inOrder.verify(restApi).fetchWithAuthRetry(eq("/sale/product-offers/3"), anyMap(), eq(AllegroProductOfferResponse.class));
    }
}
