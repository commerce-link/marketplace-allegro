package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllegroOfferExportTest {

    @Mock
    private RestApiWithRetry restApi;

    private MarketplaceOffer offer(String pimId, String ean, long price, long qty) {
        return new MarketplaceOffer(pimId, ean, "MC", "Acme", "Nazwa", "Kategoria", price, qty, 3);
    }

    private MarketplaceOffer removal(String pimId, long price) {
        return new MarketplaceOffer(pimId, null, null, null, null, null, price, 0L, 0);
    }

    private AllegroOffersResponse.OfferSummary summary(String offerId, String externalId, String amount, long available, String status) {
        return new AllegroOffersResponse.OfferSummary(
                offerId,
                externalId == null ? null : new AllegroOffersResponse.External(externalId),
                new AllegroOffersResponse.SellingMode(new AllegroOffersResponse.Price(amount, "PLN")),
                new AllegroOffersResponse.Stock(available),
                new AllegroOffersResponse.Publication(status));
    }

    private void stubOffersPage(AllegroOffersResponse.OfferSummary... summaries) {
        when(restApi.fetchWithAuthRetry(eq("/sale/offers"), anyMap(), eq(AllegroOffersResponse.class)))
                .thenReturn(new AllegroOffersResponse(List.of(summaries), summaries.length));
    }

    private void stubShippingRates() {
        when(restApi.fetchWithAuthRetry(eq("/sale/shipping-rates"), anyMap(), eq(AllegroShippingRatesResponse.class)))
                .thenReturn(new AllegroShippingRatesResponse(List.of(
                        new AllegroShippingRatesResponse.ShippingRate("rate-1", "Cennik"))));
    }

    @Test
    void createsOfferWhenNotListedYet() {
        // given
        stubOffersPage();
        stubShippingRates();
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", "5901234567890", 149L, 10L)), List.of());

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(eq("/sale/product-offers"), captor.capture(), eq(Void.class));
        AllegroOfferRequest request = (AllegroOfferRequest) captor.getValue();
        assertEquals("5901234567890", request.productSet().get(0).product().id());
        assertEquals("PIM-1", request.external().id());
        assertEquals("149.00", request.sellingMode().price().amount());
        assertEquals(10L, request.stock().available());
        assertEquals("rate-1", request.delivery().shippingRates().id());
        assertEquals("ACTIVE", request.publication().status());
        verify(restApi, never()).patchWithAuthRetry(anyString(), any(), any());
    }

    @Test
    void skipsCreateWhenQuantityIsZero() {
        // given
        stubOffersPage();
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", "5901234567890", 149L, 0L)), List.of());

        // then
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), any());
        verify(restApi, never()).fetchWithAuthRetry(eq("/sale/shipping-rates"), anyMap(), any());
    }

    @Test
    void skipsCreateWhenEanIsMissing() {
        // given
        stubOffersPage();
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", null, 149L, 5L)), List.of());

        // then
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), any());
    }

    @Test
    void skipsAllCreatesWhenAccountHasNoShippingRates() {
        // given
        stubOffersPage(summary("101", "PIM-2", "50.00", 5L, "ACTIVE"));
        when(restApi.fetchWithAuthRetry(eq("/sale/shipping-rates"), anyMap(), eq(AllegroShippingRatesResponse.class)))
                .thenReturn(new AllegroShippingRatesResponse(List.of()));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(
                offer("PIM-1", "5901234567890", 149L, 10L),
                offer("PIM-2", "5900000000000", 60L, 5L)), List.of());

        // then
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), any());
        verify(restApi).patchWithAuthRetry(eq("/sale/product-offers/101"), any(), eq(Void.class));
    }

    @Test
    void patchesOfferWhenPriceChanged() {
        // given
        stubOffersPage(summary("101", "PIM-1", "100.00", 10L, "ACTIVE"));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", "5901234567890", 149L, 10L)), List.of());

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restApi).patchWithAuthRetry(eq("/sale/product-offers/101"), captor.capture(), eq(Void.class));
        AllegroOfferRequest request = (AllegroOfferRequest) captor.getValue();
        assertEquals("149.00", request.sellingMode().price().amount());
        assertNull(request.productSet());
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), any());
    }

    @Test
    void patchesOfferWhenQuantityChanged() {
        // given
        stubOffersPage(summary("101", "PIM-1", "149.00", 3L, "ACTIVE"));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", "5901234567890", 149L, 10L)), List.of());

        // then
        verify(restApi).patchWithAuthRetry(eq("/sale/product-offers/101"), any(), eq(Void.class));
    }

    @Test
    void reactivatesEndedOfferWhenBackInStock() {
        // given
        stubOffersPage(summary("101", "PIM-1", "149.00", 10L, "ENDED"));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", "5901234567890", 149L, 10L)), List.of());

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restApi).patchWithAuthRetry(eq("/sale/product-offers/101"), captor.capture(), eq(Void.class));
        assertEquals("ACTIVE", ((AllegroOfferRequest) captor.getValue()).publication().status());
    }

    @Test
    void sendsNothingWhenOfferUnchanged() {
        // given
        stubOffersPage(summary("101", "PIM-1", "149.00", 10L, "ACTIVE"));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", "5901234567890", 149L, 10L)), List.of());

        // then
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), any());
        verify(restApi, never()).patchWithAuthRetry(anyString(), any(), any());
    }

    @Test
    void endsListedOfferForRemoval() {
        // given
        stubOffersPage(summary("101", "PIM-1", "149.00", 10L, "ACTIVE"));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(), List.of(removal("PIM-1", 149L)));

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restApi).patchWithAuthRetry(eq("/sale/product-offers/101"), captor.capture(), eq(Void.class));
        AllegroOfferRequest request = (AllegroOfferRequest) captor.getValue();
        assertEquals("ENDED", request.publication().status());
        assertNull(request.sellingMode());
        assertNull(request.stock());
    }

    @Test
    void skipsRemovalWhenOfferMissingOrAlreadyEnded() {
        // given
        stubOffersPage(summary("101", "PIM-1", "149.00", 0L, "ENDED"));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(), List.of(removal("PIM-1", 149L), removal("PIM-2", 50L)));

        // then
        verify(restApi, never()).patchWithAuthRetry(anyString(), any(), any());
    }

    @Test
    void continuesExportWhenSingleOfferFailsWithClientError() {
        // given
        stubOffersPage();
        stubShippingRates();
        when(restApi.postWithAuthRetry(eq("/sale/product-offers"), any(), eq(Void.class)))
                .thenThrow(new HttpClientException(422, "product not found"))
                .thenReturn(null);
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(
                offer("PIM-1", "5901234567890", 149L, 10L),
                offer("PIM-2", "5900000000000", 60L, 5L)), List.of());

        // then
        verify(restApi, times(2)).postWithAuthRetry(eq("/sale/product-offers"), any(), eq(Void.class));
    }

    @Test
    void rethrowsServerErrors() {
        // given
        stubOffersPage();
        stubShippingRates();
        when(restApi.postWithAuthRetry(eq("/sale/product-offers"), any(), eq(Void.class)))
                .thenThrow(new HttpClientException(500, "internal error"));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when / then
        assertThrows(HttpClientException.class, () ->
                export.export(List.of(offer("PIM-1", "5901234567890", 149L, 10L)), List.of()));
    }

    @Test
    void ignoresListedOffersWithoutExternalId() {
        // given
        stubOffersPage(summary("999", null, "149.00", 10L, "ACTIVE"));
        stubShippingRates();
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-1", "5901234567890", 149L, 10L)), List.of());

        // then
        verify(restApi).postWithAuthRetry(eq("/sale/product-offers"), any(), eq(Void.class));
        verify(restApi, never()).patchWithAuthRetry(anyString(), any(), any());
    }

    @Test
    void paginatesOfferListing() {
        // given
        AllegroOffersResponse.OfferSummary first = summary("101", "PIM-1", "149.00", 10L, "ACTIVE");
        AllegroOffersResponse.OfferSummary second = summary("102", "PIM-2", "50.00", 5L, "ACTIVE");
        when(restApi.fetchWithAuthRetry(eq("/sale/offers"), anyMap(), eq(AllegroOffersResponse.class)))
                .thenReturn(new AllegroOffersResponse(List.of(first), 2))
                .thenReturn(new AllegroOffersResponse(List.of(second), 2));
        AllegroOfferExport export = new AllegroOfferExport(restApi);

        // when
        export.export(List.of(offer("PIM-2", "5900000000000", 50L, 5L)), List.of());

        // then
        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(restApi, times(2)).fetchWithAuthRetry(eq("/sale/offers"), paramsCaptor.capture(), eq(AllegroOffersResponse.class));
        assertEquals("0", paramsCaptor.getAllValues().get(0).get("offset"));
        assertEquals("1", paramsCaptor.getAllValues().get(1).get("offset"));
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), any());
        verify(restApi, never()).patchWithAuthRetry(anyString(), any(), any());
    }
}
