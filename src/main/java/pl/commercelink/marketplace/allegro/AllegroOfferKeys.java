package pl.commercelink.marketplace.allegro;

final class AllegroOfferKeys {

    private AllegroOfferKeys() {
    }

    // The seller signature is how external systems tag their offers; offers without one are still addressable by id.
    static String of(String offerId, String externalId) {
        if (externalId != null && !externalId.isBlank()) {
            return externalId.trim();
        }
        return offerId;
    }
}
