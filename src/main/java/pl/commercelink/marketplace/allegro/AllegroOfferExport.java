package pl.commercelink.marketplace.allegro;

import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class AllegroOfferExport {

    private static final System.Logger LOGGER = System.getLogger(AllegroOfferExport.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final String STATUS_ENDED = "ENDED";
    private static final String PARAM_MANUFACTURER_CODE = "224017";
    private static final String PARAM_MODEL = "237206";

    private final RestApiWithRetry restApi;

    AllegroOfferExport(RestApiWithRetry restApi) {
        this.restApi = restApi;
    }

    void export(List<MarketplaceOffer> toPublish, List<MarketplaceOffer> toRemove) {
        Map<String, AllegroOffersResponse.OfferSummary> existing = fetchExistingOffers();
        boolean anyCreates = toPublish.stream()
                .anyMatch(o -> !existing.containsKey(o.productId()) && o.quantity() > 0
                        && o.ean() != null && !o.ean().isBlank());
        String shippingRatesId = anyCreates ? firstShippingRatesId() : null;
        String responsibleProducerId = anyCreates ? firstResponsibleProducerId() : null;

        for (MarketplaceOffer offer : toPublish) {
            AllegroOffersResponse.OfferSummary current = existing.get(offer.productId());
            try {
                if (current == null) {
                    createOffer(offer, shippingRatesId, responsibleProducerId);
                } else if (needsUpdate(offer, current)) {
                    restApi.patchWithAuthRetry("/sale/product-offers/" + current.id(),
                            AllegroOfferRequest.updateOffer(offer), Void.class);
                }
            } catch (HttpClientException e) {
                handleOfferError(offer.productId(), e);
            }
        }

        for (MarketplaceOffer offer : toRemove) {
            AllegroOffersResponse.OfferSummary current = existing.get(offer.productId());
            if (current == null || isEnded(current)) {
                continue;
            }
            try {
                restApi.patchWithAuthRetry("/sale/product-offers/" + current.id(),
                        AllegroOfferRequest.endOffer(), Void.class);
            } catch (HttpClientException e) {
                handleOfferError(offer.productId(), e);
            }
        }
    }

    private void createOffer(MarketplaceOffer offer, String shippingRatesId, String responsibleProducerId) {
        if (offer.quantity() <= 0) {
            return;
        }
        if (offer.ean() == null || offer.ean().isBlank()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping Allegro offer create for {0}: missing EAN", offer.productId());
            return;
        }
        if (shippingRatesId == null || responsibleProducerId == null) {
            return;
        }
        AllegroProductsResponse.CatalogProduct product = findCatalogProduct(offer.ean());
        if (product == null) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping Allegro offer create for {0}: EAN {1} not found in Allegro catalog",
                    offer.productId(), offer.ean());
            return;
        }
        List<String> images = product.images() == null ? List.of()
                : product.images().stream().map(AllegroProductsResponse.Image::url).toList();
        if (images.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping Allegro offer create for {0}: catalog product {1} has no images",
                    offer.productId(), product.id());
            return;
        }
        restApi.postWithAuthRetry("/sale/product-offers",
                AllegroOfferRequest.createOffer(offer, shippingRatesId, responsibleProducerId,
                        product.id(), images, missingRequiredParameters(offer, product)),
                Void.class);
    }

    private AllegroProductsResponse.CatalogProduct findCatalogProduct(String ean) {
        Map<String, String> params = new HashMap<>();
        params.put("phrase", ean);
        params.put("mode", "GTIN");
        params.put("language", "pl-PL");
        AllegroProductsResponse response =
                restApi.fetchWithAuthRetry("/sale/products", params, AllegroProductsResponse.class);
        if (response.products() == null || response.products().isEmpty()) {
            return null;
        }
        return response.products().get(0);
    }

    private List<AllegroOfferRequest.OfferParameter> missingRequiredParameters(
            MarketplaceOffer offer, AllegroProductsResponse.CatalogProduct product) {
        if (offer.manufacturerCode() == null || offer.manufacturerCode().isBlank()) {
            return List.of();
        }
        Set<String> productParameterIds = product.parameters() == null ? Set.of()
                : product.parameters().stream()
                        .map(AllegroProductsResponse.Parameter::id)
                        .collect(Collectors.toSet());
        List<AllegroOfferRequest.OfferParameter> result = new ArrayList<>();
        for (String parameterId : List.of(PARAM_MANUFACTURER_CODE, PARAM_MODEL)) {
            if (!productParameterIds.contains(parameterId)) {
                result.add(new AllegroOfferRequest.OfferParameter(
                        parameterId, List.of(offer.manufacturerCode())));
            }
        }
        return result;
    }

    private String firstShippingRatesId() {
        AllegroShippingRatesResponse response = restApi.fetchWithAuthRetry(
                "/sale/shipping-rates", Map.of(), AllegroShippingRatesResponse.class);
        if (response.shippingRates() == null || response.shippingRates().isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "No shipping rates configured on Allegro account, skipping all offer creates");
            return null;
        }
        return response.shippingRates().get(0).id();
    }

    private String firstResponsibleProducerId() {
        AllegroResponsibleProducersResponse response = restApi.fetchWithAuthRetry(
                "/sale/responsible-producers", Map.of(), AllegroResponsibleProducersResponse.class);
        if (response.responsibleProducers() == null || response.responsibleProducers().isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "No responsible producers configured on Allegro account, skipping all offer creates");
            return null;
        }
        return response.responsibleProducers().get(0).id();
    }

    private Map<String, AllegroOffersResponse.OfferSummary> fetchExistingOffers() {
        Map<String, AllegroOffersResponse.OfferSummary> result = new HashMap<>();
        int offset = 0;
        AllegroOffersResponse response;
        do {
            Map<String, String> params = new HashMap<>();
            params.put("limit", String.valueOf(PAGE_SIZE));
            params.put("offset", String.valueOf(offset));
            response = restApi.fetchWithAuthRetry("/sale/offers", params, AllegroOffersResponse.class);
            for (AllegroOffersResponse.OfferSummary summary : response.offers()) {
                if (summary.external() != null && summary.external().id() != null) {
                    result.put(summary.external().id(), summary);
                }
            }
            offset += response.offers().size();
        } while (offset < response.totalCount() && !response.offers().isEmpty());
        return result;
    }

    private boolean needsUpdate(MarketplaceOffer offer, AllegroOffersResponse.OfferSummary current) {
        if (isEnded(current)) {
            return offer.quantity() > 0;
        }
        return priceDiffers(offer, current) || quantityDiffers(offer, current);
    }

    private boolean priceDiffers(MarketplaceOffer offer, AllegroOffersResponse.OfferSummary current) {
        if (current.sellingMode() == null || current.sellingMode().price() == null
                || current.sellingMode().price().amount() == null) {
            return true;
        }
        return new BigDecimal(current.sellingMode().price().amount())
                .compareTo(BigDecimal.valueOf(offer.price())) != 0;
    }

    private boolean quantityDiffers(MarketplaceOffer offer, AllegroOffersResponse.OfferSummary current) {
        if (current.stock() == null || current.stock().available() == null) {
            return true;
        }
        return current.stock().available() != offer.quantity();
    }

    private boolean isEnded(AllegroOffersResponse.OfferSummary current) {
        return current.publication() != null && STATUS_ENDED.equals(current.publication().status());
    }

    private void handleOfferError(String productId, HttpClientException e) {
        if (e.getStatusCode() >= 500) {
            throw e;
        }
        LOGGER.log(System.Logger.Level.WARNING,
                "Skipping Allegro offer {0}: HTTP {1} {2}", productId, e.getStatusCode(), e.getResponseBody());
    }
}
