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
        Publication publication) {

    private static final String CURRENCY_PLN = "PLN";
    private static final String ID_TYPE_GTIN = "GTIN";
    private static final String STOCK_UNIT = "UNIT";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_ENDED = "ENDED";

    static AllegroOfferRequest createOffer(MarketplaceOffer offer, String shippingRatesId) {
        return new AllegroOfferRequest(
                List.of(new ProductSetItem(new Product(offer.ean(), ID_TYPE_GTIN))),
                new External(offer.productId()),
                sellingMode(offer),
                stock(offer),
                new Delivery(new ShippingRates(shippingRatesId)),
                new Publication(STATUS_ACTIVE));
    }

    static AllegroOfferRequest updateOffer(MarketplaceOffer offer) {
        return new AllegroOfferRequest(
                null,
                null,
                sellingMode(offer),
                stock(offer),
                null,
                new Publication(offer.quantity() > 0 ? STATUS_ACTIVE : STATUS_ENDED));
    }

    static AllegroOfferRequest endOffer() {
        return new AllegroOfferRequest(null, null, null, null, null, new Publication(STATUS_ENDED));
    }

    private static SellingMode sellingMode(MarketplaceOffer offer) {
        return new SellingMode(new Price(offer.price() + ".00", CURRENCY_PLN));
    }

    private static Stock stock(MarketplaceOffer offer) {
        return new Stock(offer.quantity(), STOCK_UNIT);
    }

    record ProductSetItem(Product product) {
    }

    record Product(String id, String idType) {
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
}
