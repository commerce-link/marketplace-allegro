package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

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
    record Buyer(String id, String email, String login, String firstName, String lastName, String phoneNumber) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payment(String id, String type, String finishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Fulfillment(String status) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Delivery(DeliveryAddress address, Cost cost, PickupPoint pickupPoint) {
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
    record Company(String name, String taxId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NaturalPerson(String firstName, String lastName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LineItem(Offer offer, long quantity, Price price) {
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
