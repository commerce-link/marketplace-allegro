package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroCustomerReturn(
        String id,
        String orderId,
        String referenceNumber,
        String status,
        String createdAt,
        Boolean isFulfillment,
        List<Item> items,
        List<Parcel> parcels,
        Rejection rejection
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String offerId, long quantity, String name, Price price, Reason reason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Price(String amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Reason(String type, String userComment) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Parcel(String waybill, String carrierId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rejection(String code, String reason, String createdAt) {
    }
}
