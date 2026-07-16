package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroResponsibleProducersResponse(List<ResponsibleProducer> responsibleProducers) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponsibleProducer(String id, String name) {
    }
}
