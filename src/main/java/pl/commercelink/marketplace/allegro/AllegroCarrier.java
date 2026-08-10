package pl.commercelink.marketplace.allegro;

import java.util.Arrays;

enum AllegroCarrier {

    INPOST("INPOST", "INPOST"),
    DHL("DHL", "DHL"),
    DPD("DPD", "DPD"),
    POCZTA_POLSKA("POCZTA_POLSKA", "POCZTA_POLSKA"),
    UPS("UPS", "UPS"),
    GLS("GLS", "GLS"),
    FEDEX("FEDEX", "FEDEX"),
    DB_SCHENKER("DB_SCHENKER", "DB_SCHENKER"),
    ORLEN_PACZKA("ORLEN", "ORLEN");

    private final String carrier;
    private final String carrierId;

    AllegroCarrier(String carrier, String carrierId) {
        this.carrier = carrier;
        this.carrierId = carrierId;
    }

    static String carrierIdOf(String carrier) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.carrier.equalsIgnoreCase(carrier))
                .map(candidate -> candidate.carrierId)
                .findFirst()
                .orElse(null);
    }
}
