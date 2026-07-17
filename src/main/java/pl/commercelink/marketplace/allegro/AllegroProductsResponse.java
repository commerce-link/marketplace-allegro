package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroProductsResponse(List<CatalogProduct> products) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CatalogProduct(String id, List<Parameter> parameters, List<Image> images, ProductSafety productSafety) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Parameter(String id, List<String> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Image(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductSafety(List<SafetyProducer> responsibleProducers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SafetyProducer(String id) {
    }
}
