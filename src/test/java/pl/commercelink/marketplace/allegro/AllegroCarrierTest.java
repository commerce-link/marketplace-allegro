package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AllegroCarrierTest {

    @Test
    void mapsKnownCarrierNames() {
        // when / then
        assertEquals(AllegroCarrier.INPOST, AllegroCarrier.fromCarrierName("InPost Paczkomaty"));
        assertEquals(AllegroCarrier.DPD, AllegroCarrier.fromCarrierName("DPD"));
        assertEquals(AllegroCarrier.DHL, AllegroCarrier.fromCarrierName("dhl"));
        assertEquals(AllegroCarrier.POCZTA_POLSKA, AllegroCarrier.fromCarrierName("Poczta Polska"));
        assertEquals(AllegroCarrier.UPS, AllegroCarrier.fromCarrierName("UPS"));
        assertEquals(AllegroCarrier.GLS, AllegroCarrier.fromCarrierName("GLS"));
        assertEquals(AllegroCarrier.FEDEX, AllegroCarrier.fromCarrierName("FedEx"));
        assertEquals(AllegroCarrier.DB_SCHENKER, AllegroCarrier.fromCarrierName("DB Schenker"));
    }

    @Test
    void returnsNullForUnknownOrBlankCarrier() {
        // when / then
        assertNull(AllegroCarrier.fromCarrierName("Kurier XYZ"));
        assertNull(AllegroCarrier.fromCarrierName(null));
        assertNull(AllegroCarrier.fromCarrierName("  "));
    }
}
