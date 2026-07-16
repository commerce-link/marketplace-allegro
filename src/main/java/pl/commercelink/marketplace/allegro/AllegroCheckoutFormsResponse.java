package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroCheckoutFormsResponse(List<AllegroCheckoutForm> checkoutForms, int count, int totalCount) {
}
