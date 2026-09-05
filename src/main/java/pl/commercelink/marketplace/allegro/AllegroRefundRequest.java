package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
record AllegroRefundRequest(
        Ref payment,
        Ref order,
        String commandId,
        String reason,
        List<LineItem> lineItems,
        List<Deposit> deposits,
        Delivery delivery,
        String sellerComment
) {

    record Ref(String id) {
    }

    record LineItem(String id, String type, long quantity) {
    }

    record Deposit(String lineItemId, Money totalValue) {
    }

    record Delivery(Money value) {
    }

    record Money(String amount, String currency) {
    }
}
