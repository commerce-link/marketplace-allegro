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

    private static final System.Logger LOGGER = System.getLogger(AllegroOrdersImport.class.getName());
    private static final int PAGE_SIZE = 100;
    private static final String STATUS_READY_FOR_PROCESSING = "READY_FOR_PROCESSING";
    private static final String FULFILLMENT_STATUS_NEW = "NEW";
    private static final String PAYMENT_CASH_ON_DELIVERY = "CASH_ON_DELIVERY";

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
            params.put("status", STATUS_READY_FOR_PROCESSING);
            params.put("fulfillment.status", FULFILLMENT_STATUS_NEW);
            params.put("limit", String.valueOf(PAGE_SIZE));
            params.put("offset", String.valueOf(offset));
            response = restApi.fetchWithAuthRetry("/order/checkout-forms", params, AllegroCheckoutFormsResponse.class);
            forms.addAll(response.checkoutForms());
            offset += response.checkoutForms().size();
        } while (offset < response.totalCount() && !response.checkoutForms().isEmpty());

        return forms.stream()
                .filter(this::isPaid)
                .filter(this::isImportable)
                .map(this::toMarketplaceOrder)
                .toList();
    }

    private boolean isImportable(AllegroCheckoutForm form) {
        boolean importable = form.delivery() != null && form.delivery().cost() != null
                && form.delivery().address() != null
                && form.lineItems() != null && !form.lineItems().isEmpty();
        if (!importable) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Skipping Allegro checkout form {0}: incomplete delivery/lineItems data", form.id());
        }
        return importable;
    }

    private boolean isPaid(AllegroCheckoutForm form) {
        AllegroCheckoutForm.Payment payment = form.payment();
        if (payment == null) {
            return false;
        }
        if (PAYMENT_CASH_ON_DELIVERY.equals(payment.type())) {
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
        String buyerName = buyerName(buyer);

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
                    joinName(address.firstName(), address.lastName()), address.phoneNumber(),
                    address.street(), address.zipCode(), address.city(), address.countryCode());
        }
        AllegroCheckoutForm.PickupPointAddress pointAddress = pickupPoint.address();
        return new MarketplaceCustomer.Address(
                joinName(address.firstName(), address.lastName()),
                address.phoneNumber(),
                pointAddress != null && pointAddress.street() != null ? pointAddress.street() : address.street(),
                pointAddress != null && pointAddress.zipCode() != null ? pointAddress.zipCode() : address.zipCode(),
                pointAddress != null && pointAddress.city() != null ? pointAddress.city() : address.city(),
                address.countryCode(),
                new MarketplaceCustomer.PickupPoint(pickupPoint.id(), pickupPoint.name()));
    }

    private static String joinName(String firstName, String lastName) {
        String joined = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        return joined.isEmpty() ? null : joined;
    }

    private static String buyerName(AllegroCheckoutForm.Buyer buyer) {
        String personName = joinName(buyer.firstName(), buyer.lastName());
        if (personName != null) {
            return personName;
        }
        return buyer.companyName() != null ? buyer.companyName() : buyer.login();
    }

    private String resolvePaymentType(String allegroPaymentType) {
        if (allegroPaymentType == null) {
            return "BankTransfer";
        }
        return switch (allegroPaymentType) {
            case PAYMENT_CASH_ON_DELIVERY -> "CashOnDelivery";
            case "ONLINE", "SPLIT_PAYMENT" -> "OnlinePayment";
            default -> "BankTransfer";
        };
    }
}
