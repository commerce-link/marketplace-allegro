package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllegroGpsrDictionariesTest {

    @Mock
    private RestApiWithRetry restApi;

    private void stubPersons(AllegroResponsiblePersonsResponse.ResponsiblePerson... persons) {
        when(restApi.fetchWithAuthRetry(eq("/sale/responsible-persons"), anyMap(),
                eq(AllegroResponsiblePersonsResponse.class)))
                .thenReturn(new AllegroResponsiblePersonsResponse(List.of(persons)));
    }

    private void stubProducers(AllegroResponsibleProducersResponse.ResponsibleProducer... producers) {
        when(restApi.fetchWithAuthRetry(eq("/sale/responsible-producers"), anyMap(),
                eq(AllegroResponsibleProducersResponse.class)))
                .thenReturn(new AllegroResponsibleProducersResponse(List.of(producers)));
    }

    @Test
    void matchesPersonByBrandNameIgnoringCase() {
        // given
        stubPersons(new AllegroResponsiblePersonsResponse.ResponsiblePerson("p-1", "NZXT "),
                new AllegroResponsiblePersonsResponse.ResponsiblePerson("p-2", "Domyślna"));
        AllegroGpsrDictionaries dictionaries = new AllegroGpsrDictionaries(restApi);

        // when + then
        assertEquals(Optional.of("p-1"), dictionaries.responsiblePersonId("nzxt"));
    }

    @Test
    void fallsBackToSinglePersonWhenNoNameMatch() {
        // given
        stubPersons(new AllegroResponsiblePersonsResponse.ResponsiblePerson("p-1", "Domyślna"));
        AllegroGpsrDictionaries dictionaries = new AllegroGpsrDictionaries(restApi);

        // when + then
        assertEquals(Optional.of("p-1"), dictionaries.responsiblePersonId("NZXT"));
        assertEquals(Optional.of("p-1"), dictionaries.responsiblePersonId(null));
    }

    @Test
    void returnsEmptyWhenMultiplePersonsAndNoMatch() {
        // given
        stubPersons(new AllegroResponsiblePersonsResponse.ResponsiblePerson("p-1", "Marka A"),
                new AllegroResponsiblePersonsResponse.ResponsiblePerson("p-2", "Marka B"));
        AllegroGpsrDictionaries dictionaries = new AllegroGpsrDictionaries(restApi);

        // when + then
        assertEquals(Optional.empty(), dictionaries.responsiblePersonId("NZXT"));
    }

    @Test
    void fetchesPersonsOnlyOnce() {
        // given
        stubPersons(new AllegroResponsiblePersonsResponse.ResponsiblePerson("p-1", "Domyślna"));
        AllegroGpsrDictionaries dictionaries = new AllegroGpsrDictionaries(restApi);

        // when
        dictionaries.responsiblePersonId("A");
        dictionaries.responsiblePersonId("B");

        // then
        verify(restApi, times(1)).fetchWithAuthRetry(eq("/sale/responsible-persons"), anyMap(),
                eq(AllegroResponsiblePersonsResponse.class));
    }

    @Test
    void matchesProducerByBrandNameIgnoringCase() {
        // given
        stubProducers(new AllegroResponsibleProducersResponse.ResponsibleProducer("rp-1", "NZXT"));
        AllegroGpsrDictionaries dictionaries = new AllegroGpsrDictionaries(restApi);

        // when + then
        assertEquals(Optional.of("rp-1"), dictionaries.responsibleProducerId(" nzxt"));
    }

    @Test
    void returnsEmptyProducerWhenBrandUnknownOrNull() {
        // given
        stubProducers(new AllegroResponsibleProducersResponse.ResponsibleProducer("rp-1", "MSI"));
        AllegroGpsrDictionaries dictionaries = new AllegroGpsrDictionaries(restApi);

        // when + then
        assertEquals(Optional.empty(), dictionaries.responsibleProducerId("NZXT"));
        assertEquals(Optional.empty(), dictionaries.responsibleProducerId(null));
    }

    @Test
    void nullBrandDoesNotFetchProducers() {
        // given
        AllegroGpsrDictionaries dictionaries = new AllegroGpsrDictionaries(restApi);

        // when
        dictionaries.responsibleProducerId(null);

        // then
        verifyNoInteractions(restApi);
    }
}
