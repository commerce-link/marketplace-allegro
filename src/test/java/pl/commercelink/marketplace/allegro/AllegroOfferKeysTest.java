package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AllegroOfferKeysTest {

    @Test
    void usesTheSignatureWhenSet() {
        // when
        String key = AllegroOfferKeys.of("7700000001", "BL-OLD-0001");

        // then
        assertEquals("BL-OLD-0001", key);
    }

    @Test
    void trimsTheSignature() {
        // when
        String key = AllegroOfferKeys.of("7700000001", " BL-1 ");

        // then
        assertEquals("BL-1", key);
    }

    @Test
    void fallsBackToTheOfferIdWithoutASignature() {
        // when
        String key = AllegroOfferKeys.of("7700000001", null);

        // then
        assertEquals("7700000001", key);
    }

    @Test
    void fallsBackToTheOfferIdForABlankSignature() {
        // when
        String key = AllegroOfferKeys.of("7700000001", "  ");

        // then
        assertEquals("7700000001", key);
    }
}
