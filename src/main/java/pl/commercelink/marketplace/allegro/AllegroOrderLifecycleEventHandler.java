package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonInclude;
import pl.commercelink.marketplace.api.ShipmentUpdate;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.Map;

class AllegroOrderLifecycleEventHandler {

    private static final String FULFILLMENT_NEW = "NEW";
    private static final String FULFILLMENT_PROCESSING = "PROCESSING";
    private static final String FULFILLMENT_SENT = "SENT";
    private static final String FULFILLMENT_CANCELLED = "CANCELLED";

    private final RestApiWithRetry restApi;

    AllegroOrderLifecycleEventHandler(RestApiWithRetry restApi) {
        this.restApi = restApi;
    }

    void acceptOrder(String externalOrderId) {
        AllegroCheckoutForm form = restApi.fetchWithAuthRetry(
                "/order/checkout-forms/" + externalOrderId, Map.of(), AllegroCheckoutForm.class);
        if (form.fulfillment() == null || FULFILLMENT_NEW.equals(form.fulfillment().status())) {
            updateFulfillment(externalOrderId, FULFILLMENT_PROCESSING);
        }
    }

    void shipOrder(String externalOrderId, ShipmentUpdate update) {
        if (update.trackingNo() != null) {
            restApi.postWithAuthRetry(
                    "/order/checkout-forms/" + externalOrderId + "/shipments",
                    toShipmentRequest(update),
                    Void.class);
        }
        updateFulfillment(externalOrderId, FULFILLMENT_SENT);
    }

    void cancelOrder(String externalOrderId) {
        updateFulfillment(externalOrderId, FULFILLMENT_CANCELLED);
    }

    private void updateFulfillment(String externalOrderId, String status) {
        restApi.putWithAuthRetry(
                "/order/checkout-forms/" + externalOrderId + "/fulfillment",
                new FulfillmentUpdateRequest(status),
                Void.class);
    }

    private ShipmentCreateRequest toShipmentRequest(ShipmentUpdate update) {
        AllegroCarrier carrier = AllegroCarrier.fromCarrierName(update.carrier());
        if (carrier != null) {
            return new ShipmentCreateRequest(carrier.carrierId(), update.trackingNo(), null);
        }
        return new ShipmentCreateRequest("OTHER", update.trackingNo(), update.carrier());
    }

    record FulfillmentUpdateRequest(String status) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ShipmentCreateRequest(String carrierId, String waybill, String carrierName) {
    }
}
