package pl.commercelink.marketplace.allegro;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** One page of {@code GET /billing/billing-entries}: the seller's charges and credits on the Allegro billing account. */
@JsonIgnoreProperties(ignoreUnknown = true)
record AllegroBillingEntriesResponse(List<Entry> billingEntries) {

    /**
     * @param value signed amount as Allegro sends it: a charge is negative, a credit positive (a commission refund comes
     *              as a positive amount of the charge's own type, still marked {@code DEBIT}, so read the sign, not
     *              {@code asset})
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String id, Type type, Amount value, String asset, Tax tax, Offer offer, Order order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Type(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Amount(String amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Tax(String percentage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Offer(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Order(String id) {
    }
}
