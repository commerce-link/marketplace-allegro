package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroResponsiblePersonsResponse(List<ResponsiblePerson> responsiblePersons) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ResponsiblePerson(String id, String name) {
    }
}
