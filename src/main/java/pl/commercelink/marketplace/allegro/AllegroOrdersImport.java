package pl.commercelink.marketplace.allegro;

import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProduct;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class AllegroOrdersImport {

    private static final int PAGE_SIZE = 100;

    private final RestApiWithRetry restApi;

    AllegroOrdersImport(RestApiWithRetry restApi) {
        this.restApi = restApi;
    }

    List<MarketplaceOrder> fetchOrders() {
        List<AllegroCheckoutForm> forms = new ArrayList<>();
        int offset = 0;
        AllegroCheckoutFormsResponse response;
        do {
            Map<String, String> params = new HashMap<>();
            params.put("status", "READY_FOR_PROCESSING");
            params.put("fulfillment.status", "NEW");
            params.put("limit", String.valueOf(PAGE_SIZE));
            params.put("offset", String.valueOf(offset));
            response = restApi.fetchWithAuthRetry("/order/checkout-forms", params, AllegroCheckoutFormsResponse.class);
            forms.addAll(response.checkoutForms());
            offset += response.checkoutForms().size();
        } while (offset < response.totalCount() && !response.checkoutForms().isEmpty());

        return forms.stream()
                .filter(this::isPaid)
                .map(this::toMarketplaceOrder)
                .toList();
    }

    private boolean isPaid(AllegroCheckoutForm form) {
        AllegroCheckoutForm.Payment payment = form.payment();
        if (payment == null) {
            return false;
        }
        if ("CASH_ON_DELIVERY".equals(payment.type())) {
            return true;
        }
        return payment.finishedAt() != null;
    }

    private MarketplaceOrder toMarketplaceOrder(AllegroCheckoutForm form) {
        List<MarketplaceProduct> products = form.lineItems().stream()
                .map(item -> new MarketplaceProduct(
                        item.offer().name(),
                        resolveManufacturerCode(item.offer()),
                        new BigDecimal(item.price().amount()),
                        item.quantity(),
                        BigDecimal.ZERO))
                .toList();

        return new MarketplaceOrder(
                form.id(),
                toMarketplaceCustomer(form),
                products,
                new BigDecimal(form.delivery().cost().amount()),
                resolvePaymentType(form.payment().type()),
                form.payment().id());
    }

    private String resolveManufacturerCode(AllegroCheckoutForm.Offer offer) {
        if (offer.external() != null && offer.external().id() != null) {
            return offer.external().id();
        }
        return offer.id();
    }

    private MarketplaceCustomer toMarketplaceCustomer(AllegroCheckoutForm form) {
        AllegroCheckoutForm.Buyer buyer = form.buyer();
        String buyerName = buyer.firstName() + " " + buyer.lastName();

        MarketplaceCustomer.Address shippingAddress = toShippingAddress(form.delivery());

        AllegroCheckoutForm.Company company = form.invoice() != null && form.invoice().address() != null
                ? form.invoice().address().company()
                : null;

        if (company != null) {
            AllegroCheckoutForm.InvoiceAddress invoiceAddress = form.invoice().address();
            MarketplaceCustomer.Address billingAddress = new MarketplaceCustomer.Address(
                    company.name(),
                    buyer.phoneNumber(),
                    invoiceAddress.street(),
                    invoiceAddress.zipCode(),
                    invoiceAddress.city(),
                    invoiceAddress.countryCode());
            return new MarketplaceCustomer(
                    MarketplaceCustomer.CustomerType.COMPANY,
                    buyerName,
                    company.name(),
                    buyer.email(),
                    buyer.phoneNumber(),
                    company.taxId(),
                    billingAddress,
                    shippingAddress);
        }

        return new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL,
                buyerName,
                null,
                buyer.email(),
                buyer.phoneNumber(),
                null,
                shippingAddress,
                shippingAddress);
    }

    private MarketplaceCustomer.Address toShippingAddress(AllegroCheckoutForm.Delivery delivery) {
        AllegroCheckoutForm.DeliveryAddress address = delivery.address();
        AllegroCheckoutForm.PickupPoint pickupPoint = delivery.pickupPoint();
        if (pickupPoint == null) {
            return new MarketplaceCustomer.Address(
                    address.firstName() + " " + address.lastName(), address.phoneNumber(),
                    address.street(), address.zipCode(), address.city(), address.countryCode());
        }
        AllegroCheckoutForm.PickupPointAddress pointAddress = pickupPoint.address();
        return new MarketplaceCustomer.Address(
                address.firstName() + " " + address.lastName(),
                address.phoneNumber(),
                pickupPoint.id() + " — " + pickupPoint.name()
                        + (pointAddress != null && pointAddress.street() != null ? ", " + pointAddress.street() : ""),
                pointAddress != null ? pointAddress.zipCode() : address.zipCode(),
                pointAddress != null ? pointAddress.city() : address.city(),
                address.countryCode());
    }

    private String resolvePaymentType(String allegroPaymentType) {
        return switch (allegroPaymentType) {
            case "CASH_ON_DELIVERY" -> "CashOnDelivery";
            case "ONLINE", "SPLIT_PAYMENT" -> "OnlinePayment";
            default -> "BankTransfer";
        };
    }
}
