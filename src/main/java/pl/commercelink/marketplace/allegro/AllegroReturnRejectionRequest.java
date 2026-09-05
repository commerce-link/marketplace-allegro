package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
record AllegroReturnRejectionRequest(Rejection rejection) {

    public static final String CODE_REFUND_REJECTED = "REFUND_REJECTED";

    static AllegroReturnRejectionRequest refundRejected(String reason) {
        return new AllegroReturnRejectionRequest(new Rejection(CODE_REFUND_REJECTED, reason));
    }

    record Rejection(String code, String reason) {
    }
}
