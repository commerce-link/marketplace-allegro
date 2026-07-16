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
    void mapsAliasesSubsumedByOtherEntriesAfterCleanup() {
        // when / then
        assertEquals(AllegroCarrier.INPOST, AllegroCarrier.fromCarrierName("Paczkomaty InPost"));
        assertEquals(AllegroCarrier.POCZTA_POLSKA, AllegroCarrier.fromCarrierName("Pocztex"));
        assertEquals(AllegroCarrier.POCZTA_POLSKA, AllegroCarrier.fromCarrierName("PocztaPolska"));
        assertEquals(AllegroCarrier.DB_SCHENKER, AllegroCarrier.fromCarrierName("DBSchenker"));
        assertEquals(AllegroCarrier.DB_SCHENKER, AllegroCarrier.fromCarrierName("DB Schenker"));
        assertEquals(AllegroCarrier.FEDEX, AllegroCarrier.fromCarrierName("Fedex"));
        assertEquals(AllegroCarrier.ORLEN_PACZKA, AllegroCarrier.fromCarrierName("Orlen Paczka"));
        assertEquals(AllegroCarrier.ORLEN_PACZKA, AllegroCarrier.fromCarrierName("RUCH"));
    }

    @Test
    void orlenPaczkaCarrierIdIsOrlen() {
        // when / then
        assertEquals("ORLEN", AllegroCarrier.ORLEN_PACZKA.carrierId());
    }

    @Test
    void returnsNullForUnknownOrBlankCarrier() {
        // when / then
        assertNull(AllegroCarrier.fromCarrierName("Kurier XYZ"));
        assertNull(AllegroCarrier.fromCarrierName(null));
        assertNull(AllegroCarrier.fromCarrierName("  "));
    }
}
