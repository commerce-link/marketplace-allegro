package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AllegroCarrierTest {

    @Test
    void translatesTheCanonicalCarrierToAllegroIdentifier() {
        // when / then
        assertEquals("INPOST", AllegroCarrier.carrierIdOf("INPOST"));
        assertEquals("ORLEN", AllegroCarrier.carrierIdOf("ORLEN"));
        assertEquals("POCZTA_POLSKA", AllegroCarrier.carrierIdOf("poczta_polska"));
    }

    @Test
    void returnsNothingForCarriersAllegroDoesNotKnow() {
        // when / then
        assertNull(AllegroCarrier.carrierIdOf("ZABKA"));
        assertNull(AllegroCarrier.carrierIdOf(null));
    }
}
