package pl.commercelink.marketplace.allegro;

import pl.commercelink.marketplace.api.AliasedCarrier;

import java.util.List;

enum AllegroCarrier implements AliasedCarrier {

    INPOST("INPOST", List.of("Paczkomat")),
    DHL("DHL", List.of()),
    DPD("DPD", List.of()),
    POCZTA_POLSKA("POCZTA_POLSKA", List.of("Poczta", "Pocztex")),
    UPS("UPS", List.of()),
    GLS("GLS", List.of()),
    FEDEX("FEDEX", List.of()),
    DB_SCHENKER("DB_SCHENKER", List.of("Schenker")),
    ORLEN_PACZKA("ORLEN", List.of("Orlen", "RUCH"));

    private final String carrierId;
    private final List<String> aliases;

    AllegroCarrier(String carrierId, List<String> aliases) {
        this.carrierId = carrierId;
        this.aliases = aliases;
    }

    String carrierId() {
        return carrierId;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    static AllegroCarrier fromCarrierName(String carrierName) {
        return AliasedCarrier.deserialize(values(), carrierName);
    }
}
