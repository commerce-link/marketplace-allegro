package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroOffersResponse(List<OfferSummary> offers, int totalCount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OfferSummary(String id, External external, SellingMode sellingMode, Stock stock, Publication publication) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record External(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SellingMode(Price price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Price(String amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stock(Long available) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Publication(String status) {
    }
}
