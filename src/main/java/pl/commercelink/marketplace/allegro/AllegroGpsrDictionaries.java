package pl.commercelink.marketplace.allegro;

import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class AllegroGpsrDictionaries {

    private final RestApiWithRetry restApi;
    private List<AllegroResponsiblePersonsResponse.ResponsiblePerson> persons;
    private List<AllegroResponsibleProducersResponse.ResponsibleProducer> producers;

    AllegroGpsrDictionaries(RestApiWithRetry restApi) {
        this.restApi = restApi;
    }

    Optional<String> responsiblePersonId(String brand) {
        if (persons == null) {
            AllegroResponsiblePersonsResponse response = restApi.fetchWithAuthRetry(
                    "/sale/responsible-persons", Map.of(), AllegroResponsiblePersonsResponse.class);
            persons = response.responsiblePersons() == null ? List.of() : response.responsiblePersons();
        }
        if (brand != null) {
            for (AllegroResponsiblePersonsResponse.ResponsiblePerson person : persons) {
                if (nameMatches(person.name(), brand)) {
                    return Optional.of(person.id());
                }
            }
        }
        if (persons.size() == 1) {
            return Optional.of(persons.get(0).id());
        }
        return Optional.empty();
    }

    Optional<String> responsibleProducerId(String brand) {
        if (brand == null) {
            return Optional.empty();
        }
        if (producers == null) {
            AllegroResponsibleProducersResponse response = restApi.fetchWithAuthRetry(
                    "/sale/responsible-producers", Map.of(), AllegroResponsibleProducersResponse.class);
            producers = response.responsibleProducers() == null ? List.of() : response.responsibleProducers();
        }
        for (AllegroResponsibleProducersResponse.ResponsibleProducer producer : producers) {
            if (nameMatches(producer.name(), brand)) {
                return Optional.of(producer.id());
            }
        }
        return Optional.empty();
    }

    private boolean nameMatches(String dictionaryName, String brand) {
        return dictionaryName != null && dictionaryName.trim().equalsIgnoreCase(brand.trim());
    }
}
