package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AllegroEstimatedShippingAtTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AllegroCheckoutForm.Delivery delivery(String json) throws Exception {
        return MAPPER.readValue(json, AllegroCheckoutForm.Delivery.class);
    }

    @Test
    @DisplayName("the dispatch deadline becomes the estimated shipping date")
    void dispatchDeadlineIsParsed() throws Exception {
        AllegroCheckoutForm.Delivery delivery = delivery(
                "{\"time\": {\"from\": \"2026-09-05T08:00:00Z\", \"to\": \"2026-09-07T20:00:00Z\", " +
                        "\"dispatch\": {\"from\": \"2026-09-02T10:00:00Z\", \"to\": \"2026-09-03T16:00:00Z\"}}}");

        assertEquals(LocalDate.of(2026, 9, 3), delivery.toEstimatedShippingAt());
    }

    @Test
    @DisplayName("the deadline is read in the seller time zone, not in UTC")
    void deadlineIsReadInSellerTimeZone() throws Exception {
        AllegroCheckoutForm.Delivery delivery = delivery(
                "{\"time\": {\"dispatch\": {\"to\": \"2026-09-03T22:30:00Z\"}}}");

        assertEquals(LocalDate.of(2026, 9, 4), delivery.toEstimatedShippingAt());
    }

    @Test
    @DisplayName("an offset other than UTC is honoured")
    void nonUtcOffsetIsHonoured() throws Exception {
        AllegroCheckoutForm.Delivery delivery = delivery(
                "{\"time\": {\"dispatch\": {\"to\": \"2026-09-03T16:00:00+02:00\"}}}");

        assertEquals(LocalDate.of(2026, 9, 3), delivery.toEstimatedShippingAt());
    }

    @Test
    @DisplayName("delivery time without a dispatch section yields no date")
    void missingDispatchYieldsNoDate() throws Exception {
        AllegroCheckoutForm.Delivery withoutDispatch = delivery(
                "{\"time\": {\"from\": \"2026-09-05T08:00:00Z\", \"to\": \"2026-09-07T20:00:00Z\"}}");
        AllegroCheckoutForm.Delivery withoutTime = delivery("{}");

        assertNull(withoutDispatch.toEstimatedShippingAt());
        assertNull(withoutTime.toEstimatedShippingAt());
    }

    @Test
    @DisplayName("an unparsable deadline does not break the import")
    void unparsableDeadlineYieldsNoDate() throws Exception {
        AllegroCheckoutForm.Delivery delivery = delivery(
                "{\"time\": {\"dispatch\": {\"to\": \"as soon as possible\"}}}");

        assertNull(delivery.toEstimatedShippingAt());
    }

    @Test
    @DisplayName("the delivery window to the buyer is not used as the shipping date")
    void deliveryWindowIsNotUsed() throws Exception {
        AllegroCheckoutForm.Delivery delivery = delivery(
                "{\"time\": {\"from\": \"2026-09-05T08:00:00Z\", \"to\": \"2026-09-07T20:00:00Z\", " +
                        "\"dispatch\": {\"to\": \"2026-09-03T16:00:00Z\"}}}");

        assertEquals(LocalDate.of(2026, 9, 3), delivery.toEstimatedShippingAt());
        assertEquals("2026-09-07T20:00:00Z", delivery.time().to());
    }
}
