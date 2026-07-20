package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroCategoryParametersResponse(List<CategoryParameter> parameters) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CategoryParameter(String id) {
    }
}
