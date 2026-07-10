package pl.commercelink.marketplace.allegro;

import pl.commercelink.marketplace.api.InvoiceUpdate;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.ShipmentUpdate;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.List;

class AllegroMarketplaceProvider implements MarketplaceProvider {

    private final AllegroOrdersImport ordersImport;
    private final AllegroOrderLifecycleEventHandler lifecycleHandler;

    AllegroMarketplaceProvider(RestApiWithRetry restApi) {
        this.ordersImport = new AllegroOrdersImport(restApi);
        this.lifecycleHandler = new AllegroOrderLifecycleEventHandler(restApi);
    }

    @Override
    public List<MarketplaceOrder> fetchOrders() {
        return ordersImport.fetchOrders();
    }

    @Override
    public void exportOffers(List<MarketplaceOffer> toPublish, List<MarketplaceOffer> toRemove) {
    }

    @Override
    public void acceptOrder(String externalOrderId) {
        lifecycleHandler.acceptOrder(externalOrderId);
    }

    @Override
    public void shipOrder(String externalOrderId, ShipmentUpdate update) {
        lifecycleHandler.shipOrder(externalOrderId, update);
    }

    @Override
    public void cancelOrder(String externalOrderId) {
        lifecycleHandler.cancelOrder(externalOrderId);
    }

    @Override
    public void updateInvoice(String externalOrderId, InvoiceUpdate update) {
    }
}
