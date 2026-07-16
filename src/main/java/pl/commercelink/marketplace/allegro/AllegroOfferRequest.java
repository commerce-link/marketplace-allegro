package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonInclude;
import pl.commercelink.marketplace.api.MarketplaceOffer;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record AllegroOfferRequest(
        List<ProductSetItem> productSet,
        External external,
        SellingMode sellingMode,
        Stock stock,
        Delivery delivery,
        Publication publication,
        List<String> images,
        List<OfferParameter> parameters) {

    private static final String CURRENCY_PLN = "PLN";
    private static final String STOCK_UNIT = "UNIT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ENDED = "ENDED";
    private static final String SAFETY_TYPE_TEXT = "TEXT";
    private static final String SAFETY_TEXT = "Szczegółowe informacje o bezpieczeństwie produktu dostępne są u producenta.";

    static AllegroOfferRequest createOffer(MarketplaceOffer offer, String shippingRatesId,
                                           String responsibleProducerId, String productId,
                                           List<String> images, List<OfferParameter> parameters) {
        return new AllegroOfferRequest(
                List.of(new ProductSetItem(
                        new Product(productId),
                        new ResponsibleProducer(responsibleProducerId),
                        new SafetyInformation(SAFETY_TYPE_TEXT, SAFETY_TEXT))),
                new External(offer.productId()),
                sellingMode(offer),
                stock(offer),
                new Delivery(new ShippingRates(shippingRatesId)),
                new Publication(STATUS_ACTIVE),
                images,
                parameters.isEmpty() ? null : parameters);
    }

    static AllegroOfferRequest updateOffer(MarketplaceOffer offer) {
        return new AllegroOfferRequest(
                null,
                null,
                sellingMode(offer),
                stock(offer),
                null,
                new Publication(offer.quantity() > 0 ? STATUS_ACTIVE : STATUS_ENDED),
                null,
                null);
    }

    static AllegroOfferRequest endOffer() {
        return new AllegroOfferRequest(null, null, null, null, null, new Publication(STATUS_ENDED), null, null);
    }

    private static SellingMode sellingMode(MarketplaceOffer offer) {
        return new SellingMode(new Price(offer.price() + ".00", CURRENCY_PLN));
    }

    private static Stock stock(MarketplaceOffer offer) {
        return new Stock(offer.quantity(), STOCK_UNIT);
    }

    record ProductSetItem(Product product, ResponsibleProducer responsibleProducer, SafetyInformation safetyInformation) {
    }

    record Product(String id) {
    }

    record ResponsibleProducer(String id) {
    }

    record SafetyInformation(String type, String description) {
    }

    record External(String id) {
    }

    record SellingMode(Price price) {
    }

    record Price(String amount, String currency) {
    }

    record Stock(long available, String unit) {
    }

    record Delivery(ShippingRates shippingRates) {
    }

    record ShippingRates(String id) {
    }

    record Publication(String status) {
    }

    record OfferParameter(String id, List<String> values) {
    }
}
