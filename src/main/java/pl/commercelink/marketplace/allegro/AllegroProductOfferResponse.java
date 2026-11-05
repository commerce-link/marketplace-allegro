package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroProductOfferResponse(String id, List<ProductSetItem> productSet) {

    // A bundle offer carries several catalogue products; every EAN of every product identifies the same offer
    List<String> eans() {
        if (productSet == null) {
            return List.of();
        }
        Set<String> eans = new LinkedHashSet<>();
        productSet.stream()
                .filter(Objects::nonNull)
                .map(ProductSetItem::product)
                .filter(product -> product != null && product.parameters() != null)
                .flatMap(product -> product.parameters().stream())
                .filter(parameter -> parameter != null
                        && AllegroOffersListing.EAN_PARAMETER_ID.equals(parameter.id())
                        && parameter.values() != null)
                .flatMap(parameter -> parameter.values().stream())
                .filter(value -> value != null && !value.isBlank())
                .forEach(eans::add);
        return List.copyOf(eans);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProductSetItem(Product product) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Product(String id, List<Parameter> parameters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Parameter(String id, List<String> values) {
    }
}
