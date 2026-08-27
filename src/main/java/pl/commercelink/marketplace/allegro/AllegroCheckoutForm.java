package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroCheckoutForm(
        String id,
        String status,
        Buyer buyer,
        Payment payment,
        Fulfillment fulfillment,
        Delivery delivery,
        Invoice invoice,
        List<LineItem> lineItems
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Buyer(String id, String email, String login, String firstName, String lastName, String companyName,
                 String phoneNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payment(String id, String type, String finishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fulfillment(String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Delivery(DeliveryAddress address, Cost cost, PickupPoint pickupPoint, Method method, Time time) {

        Delivery(DeliveryAddress address, Cost cost, PickupPoint pickupPoint, Method method) {
            this(address, cost, pickupPoint, method, null);
        }

        LocalDate toEstimatedShippingAt() {
            return time == null ? null : time.toEstimatedShippingAt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Time(String from, String to, Dispatch dispatch) {

        LocalDate toEstimatedShippingAt() {
            return dispatch == null ? null : dispatch.toEstimatedShippingAt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Dispatch(String from, String to) {

        private static final ZoneId SELLER_ZONE = ZoneId.of("Europe/Warsaw");

        LocalDate toEstimatedShippingAt() {
            if (to == null || to.isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(to.trim()).atZoneSameInstant(SELLER_ZONE).toLocalDate();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Method(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PickupPoint(String id, String name, PickupPointAddress address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PickupPointAddress(String street, String zipCode, String city) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeliveryAddress(String firstName, String lastName, String companyName, String street,
                           String city, String zipCode, String countryCode, String phoneNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Cost(String amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Invoice(Boolean required, InvoiceAddress address) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record InvoiceAddress(String street, String city, String zipCode, String countryCode,
                          Company company, NaturalPerson naturalPerson) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Company(String name, String taxId, List<CompanyId> ids) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CompanyId(String type, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NaturalPerson(String firstName, String lastName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LineItem(String id, Offer offer, long quantity, Price price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Offer(String id, String name, External external) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record External(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Price(String amount, String currency) {
    }
}
