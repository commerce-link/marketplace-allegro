package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroListedOffersResponse(List<Offer> offers, Integer count, Integer totalCount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Offer(String id, String name, External external, Stock stock, SellingMode sellingMode, Publication publication) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record External(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stock(Long available) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SellingMode(Price price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Price(String amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Publication(String status) {
    }
}
