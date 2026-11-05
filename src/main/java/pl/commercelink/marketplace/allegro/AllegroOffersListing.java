package pl.commercelink.marketplace.allegro;

import pl.commercelink.marketplace.api.MarketplaceListedOffer;
import pl.commercelink.marketplace.api.MarketplaceOffers;
import pl.commercelink.marketplace.api.MarketplaceOffersPage;
import pl.commercelink.marketplace.api.MarketplaceRateLimitedException;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

class AllegroOffersListing implements MarketplaceOffers {

    static final int PAGE_SIZE = 50;
    static final String EAN_PARAMETER_ID = "225693";
    // rest-client does not expose response headers; Allegro documents 429 without Retry-After and a one-minute block
    static final Duration RATE_LIMIT_RETRY_AFTER = Duration.ofSeconds(60);

    private static final String ENDED = "ENDED";
    private static final String ACTIVE = "ACTIVE";

    private final RestApiWithRetry restApi;

    AllegroOffersListing(RestApiWithRetry restApi) {
        this.restApi = restApi;
    }

    @Override
    public MarketplaceOffersPage page(String cursor) {
        int offset = parseOffset(cursor);

        Map<String, String> params = new HashMap<>();
        params.put("limit", String.valueOf(PAGE_SIZE));
        params.put("offset", String.valueOf(offset));
        AllegroListedOffersResponse response = call(() ->
                restApi.fetchWithAuthRetry("/sale/offers", params, AllegroListedOffersResponse.class));

        List<AllegroListedOffersResponse.Offer> offers = response.offers() == null ? List.of() : response.offers();
        List<MarketplaceListedOffer> listed = new ArrayList<>();
        for (AllegroListedOffersResponse.Offer offer : offers) {
            // Ended offers cannot be linked to anything sellable, so their details are not worth a request
            if (offer.publication() != null && ENDED.equals(offer.publication().status())) {
                continue;
            }
            AllegroProductOfferResponse details = details(offer.id());
            if (details != null) {
                listed.add(toListedOffer(offer, details));
            }
        }

        Integer totalCount = response.totalCount();
        int count = response.count() != null ? response.count() : offers.size();
        // The walk advances on the listed page, not on the kept offers: a page of ended offers must not stop it,
        // while an empty listed page ends it even if totalCount promises more, so a shrinking catalogue cannot loop
        boolean last = offers.isEmpty()
                || (totalCount != null ? offset + count >= totalCount : count < PAGE_SIZE);
        return new MarketplaceOffersPage(listed, last ? null : String.valueOf(offset + PAGE_SIZE), totalCount);
    }

    private AllegroProductOfferResponse details(String offerId) {
        try {
            return call(() -> restApi.fetchWithAuthRetry(
                    "/sale/product-offers/" + offerId, Map.of(), AllegroProductOfferResponse.class));
        } catch (HttpClientException e) {
            // The offer was deleted between the list and the detail request
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientException e) {
            if (e.getStatusCode() == 429) {
                // The app re-reads the whole page from the same cursor, so a partial page is never returned
                throw new MarketplaceRateLimitedException(RATE_LIMIT_RETRY_AFTER);
            }
            throw e;
        }
    }

    private MarketplaceListedOffer toListedOffer(AllegroListedOffersResponse.Offer offer, AllegroProductOfferResponse details) {
        String externalId = offer.external() == null ? null : offer.external().id();
        long quantity = offer.stock() == null || offer.stock().available() == null ? 0 : offer.stock().available();
        BigDecimal price = offer.sellingMode() == null || offer.sellingMode().price() == null
                || offer.sellingMode().price().amount() == null
                ? null : new BigDecimal(offer.sellingMode().price().amount());
        boolean active = offer.publication() != null && ACTIVE.equals(offer.publication().status());
        return new MarketplaceListedOffer(
                AllegroOfferKeys.of(offer.id(), externalId), details.eans(), offer.name(), quantity, price, active);
    }

    private int parseOffset(String cursor) {
        if (cursor == null) {
            return 0;
        }
        try {
            int offset = Integer.parseInt(cursor);
            if (offset < 0) {
                throw new IllegalArgumentException("Invalid Allegro offers cursor: " + cursor);
            }
            return offset;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Allegro offers cursor: " + cursor, e);
        }
    }
}
