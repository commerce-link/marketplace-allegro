package pl.commercelink.marketplace.allegro;

record AllegroRejection(String code, String message, System.Logger.Level level) {

    static AllegroRejection newOfferWithoutQuantity() {
        return new AllegroRejection("NEW_OFFER_WITHOUT_QUANTITY",
                "Offer is not listed on Allegro yet and has no available quantity",
                System.Logger.Level.DEBUG);
    }

    static AllegroRejection missingEan() {
        return new AllegroRejection("MISSING_EAN",
                "Product has no EAN, which Allegro requires to match a catalog product",
                System.Logger.Level.WARNING);
    }

    static AllegroRejection noShippingRates() {
        return new AllegroRejection("NO_SHIPPING_RATES",
                "No shipping rates available on the Allegro account, offer cannot be created",
                System.Logger.Level.WARNING);
    }

    static AllegroRejection eanNotInCatalog(String ean) {
        return new AllegroRejection("EAN_NOT_IN_CATALOG",
                "EAN " + ean + " was not found in the Allegro catalog",
                System.Logger.Level.WARNING);
    }

    static AllegroRejection catalogProductWithoutImages(String catalogProductId) {
        return new AllegroRejection("CATALOG_PRODUCT_WITHOUT_IMAGES",
                "Allegro catalog product " + catalogProductId + " has no images",
                System.Logger.Level.WARNING);
    }

    static AllegroRejection noResponsibleProducer(String catalogProductId, String brand) {
        return new AllegroRejection("NO_RESPONSIBLE_PRODUCER",
                "Allegro catalog product " + catalogProductId + " has no responsible producer"
                        + " and the account dictionary has no entry named \"" + brand + "\"",
                System.Logger.Level.WARNING);
    }

    static AllegroRejection httpError(int statusCode, String responseBody) {
        return new AllegroRejection("HTTP_" + statusCode, responseBody, System.Logger.Level.WARNING);
    }
}
