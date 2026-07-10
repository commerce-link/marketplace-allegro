package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.api.ShipmentUpdate;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AllegroOrderLifecycleEventHandlerTest {

    private static final String ORDER_ID = "21f69d0a-9d63-11ee-b9d1-0242ac120002";

    @Mock
    private RestApiWithRetry restApi;

    private AllegroCheckoutForm formWithFulfillment(String fulfillmentStatus) {
        return new AllegroCheckoutForm(ORDER_ID, "READY_FOR_PROCESSING", null, null,
                new AllegroCheckoutForm.Fulfillment(fulfillmentStatus), null, null, List.of());
    }

    @Test
    void acceptOrderSetsProcessingWhenFulfillmentIsNew() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID), anyMap(), eq(AllegroCheckoutForm.class)))
                .thenReturn(formWithFulfillment("NEW"));
        AllegroOrderLifecycleEventHandler handler = new AllegroOrderLifecycleEventHandler(restApi);

        // when
        handler.acceptOrder(ORDER_ID);

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restApi).putWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID + "/fulfillment"),
                captor.capture(), eq(Void.class));
        assertEquals("PROCESSING",
                ((AllegroOrderLifecycleEventHandler.FulfillmentUpdateRequest) captor.getValue()).status());
    }

    @Test
    void acceptOrderIsNoOpWhenAlreadyProcessing() {
        // given
        when(restApi.fetchWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID), anyMap(), eq(AllegroCheckoutForm.class)))
                .thenReturn(formWithFulfillment("PROCESSING"));
        AllegroOrderLifecycleEventHandler handler = new AllegroOrderLifecycleEventHandler(restApi);

        // when
        handler.acceptOrder(ORDER_ID);

        // then
        verify(restApi, never()).putWithAuthRetry(anyString(), any(), any());
    }

    @Test
    void shipOrderCreatesShipmentAndSetsSent() {
        // given
        AllegroOrderLifecycleEventHandler handler = new AllegroOrderLifecycleEventHandler(restApi);

        // when
        handler.shipOrder(ORDER_ID, new ShipmentUpdate("PX123", "DPD", "https://tracking/PX123"));

        // then
        InOrder inOrder = inOrder(restApi);
        ArgumentCaptor<Object> shipmentCaptor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(restApi).postWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID + "/shipments"),
                shipmentCaptor.capture(), eq(Void.class));
        var shipment = (AllegroOrderLifecycleEventHandler.ShipmentCreateRequest) shipmentCaptor.getValue();
        assertEquals("DPD", shipment.carrierId());
        assertEquals("PX123", shipment.waybill());
        assertNull(shipment.carrierName());

        ArgumentCaptor<Object> statusCaptor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(restApi).putWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID + "/fulfillment"),
                statusCaptor.capture(), eq(Void.class));
        assertEquals("SENT",
                ((AllegroOrderLifecycleEventHandler.FulfillmentUpdateRequest) statusCaptor.getValue()).status());
    }

    @Test
    void shipOrderUsesOtherCarrierWithNameWhenUnknown() {
        // given
        AllegroOrderLifecycleEventHandler handler = new AllegroOrderLifecycleEventHandler(restApi);

        // when
        handler.shipOrder(ORDER_ID, new ShipmentUpdate("PX999", "Kurier XYZ", null));

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restApi).postWithAuthRetry(anyString(), captor.capture(), eq(Void.class));
        var shipment = (AllegroOrderLifecycleEventHandler.ShipmentCreateRequest) captor.getValue();
        assertEquals("OTHER", shipment.carrierId());
        assertEquals("Kurier XYZ", shipment.carrierName());
    }

    @Test
    void shipOrderWithoutTrackingOnlySetsSent() {
        // given
        AllegroOrderLifecycleEventHandler handler = new AllegroOrderLifecycleEventHandler(restApi);

        // when
        handler.shipOrder(ORDER_ID, new ShipmentUpdate(null, null, null));

        // then
        verify(restApi, never()).postWithAuthRetry(anyString(), any(), any());
        verify(restApi).putWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID + "/fulfillment"),
                any(), eq(Void.class));
    }

    @Test
    void cancelOrderSetsCancelledFulfillment() {
        // given
        AllegroOrderLifecycleEventHandler handler = new AllegroOrderLifecycleEventHandler(restApi);

        // when
        handler.cancelOrder(ORDER_ID);

        // then
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(restApi).putWithAuthRetry(eq("/order/checkout-forms/" + ORDER_ID + "/fulfillment"),
                captor.capture(), eq(Void.class));
        assertEquals("CANCELLED",
                ((AllegroOrderLifecycleEventHandler.FulfillmentUpdateRequest) captor.getValue()).status());
    }
}
