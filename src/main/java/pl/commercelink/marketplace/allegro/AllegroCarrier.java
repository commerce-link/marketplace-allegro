package pl.commercelink.marketplace.allegro;

import java.util.List;

enum AllegroCarrier {

    INPOST("INPOST", List.of("Paczkomat")),
    DHL("DHL", List.of()),
    DPD("DPD", List.of()),
    POCZTA_POLSKA("POCZTA_POLSKA", List.of("Poczta", "Pocztex")),
    UPS("UPS", List.of()),
    GLS("GLS", List.of()),
    FEDEX("FEDEX", List.of()),
    DB_SCHENKER("DB_SCHENKER", List.of("Schenker")),
    ORLEN_PACZKA("ORLEN_PACZKA", List.of("Orlen", "RUCH"));

    private final String carrierId;
    private final List<String> aliases;

    AllegroCarrier(String carrierId, List<String> aliases) {
        this.carrierId = carrierId;
        this.aliases = aliases;
    }

    String carrierId() {
        return carrierId;
    }

    static AllegroCarrier fromCarrierName(String carrierName) {
        if (carrierName == null || carrierName.isBlank()) {
            return null;
        }
        String normalized = carrierName.trim().toUpperCase();
        for (AllegroCarrier carrier : values()) {
            if (normalized.contains(carrier.name().replace('_', ' ')) || normalized.contains(carrier.name())) {
                return carrier;
            }
            for (String alias : carrier.aliases) {
                if (normalized.contains(alias.toUpperCase())) {
                    return carrier;
                }
            }
        }
        return null;
    }
}
