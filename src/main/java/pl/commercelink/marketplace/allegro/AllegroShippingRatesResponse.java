package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroShippingRatesResponse(List<ShippingRate> shippingRates) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ShippingRate(String id, String name) {
    }
}
