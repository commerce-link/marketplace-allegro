package pl.commercelink.marketplace.allegro;

record AllegroReturnRejectionRequest(Rejection rejection) {

    static AllegroReturnRejectionRequest refundRejected(String reason) {
        return new AllegroReturnRejectionRequest(new Rejection("REFUND_REJECTED", reason));
    }

    record Rejection(String code, String reason) {
    }
}
