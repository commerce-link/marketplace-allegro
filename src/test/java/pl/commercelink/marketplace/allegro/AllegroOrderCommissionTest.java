package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.rest.client.HttpClientException;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllegroOrderCommissionTest {

    private static final String FORM_ID = "29b3b2a0-c60d-11f1-a870-7b4a0514b9e7";

    @Mock
    private RestApiWithRetry restApi;

    private static AllegroCheckoutForm.LineItem line(String offerId) {
        return new AllegroCheckoutForm.LineItem(null,
                new AllegroCheckoutForm.Offer(offerId, "Product " + offerId, null),
                1, new AllegroCheckoutForm.Price("100.00", "PLN"), null);
    }

    private static AllegroBillingEntriesResponse.Entry entry(String type, String amount, String offerId) {
        return new AllegroBillingEntriesResponse.Entry("e-" + type + amount, new AllegroBillingEntriesResponse.Type(type, type),
                new AllegroBillingEntriesResponse.Amount(amount, "PLN"), "DEBIT",
                new AllegroBillingEntriesResponse.Tax("23.00"),
                offerId == null ? null : new AllegroBillingEntriesResponse.Offer(offerId),
                new AllegroBillingEntriesResponse.Order(FORM_ID));
    }

    private void billingReturns(AllegroBillingEntriesResponse.Entry... entries) {
        when(restApi.fetchWithAuthRetry(eq("/billing/billing-entries"), anyMap(), eq(AllegroBillingEntriesResponse.class)))
                .thenReturn(new AllegroBillingEntriesResponse(List.of(entries)));
    }

    private List<BigDecimal> forLines(AllegroCheckoutForm.LineItem... lines) {
        return new AllegroOrderCommission(restApi).forLines(FORM_ID, List.of(lines));
    }

    private static List<BigDecimal> amounts(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }

    @Test
    void sucEntryLandsOnLineWithMatchingOffer() {
        // given
        billingReturns(entry("SUC", "-10.10", "offer-2"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then
        assertEquals(amounts("0.00", "10.10"), commission);
        verify(restApi).fetchWithAuthRetry(eq("/billing/billing-entries"),
                argThat(params -> FORM_ID.equals(params.get("order.id")) && "100".equals(params.get("limit"))
                        && "0".equals(params.get("offset"))),
                eq(AllegroBillingEntriesResponse.class));
    }

    @Test
    void entriesOfOtherTypesAreIgnored() {
        // given: a listing fee and an Allegro delivery charge on the same order are not commission
        billingReturns(entry("SUC", "-5.00", "offer-1"), entry("LIS", "-1.00", "offer-1"),
                entry("DTR", "-12.00", "offer-1"), entry("REF", "3.00", "offer-1"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"));

        // then
        assertEquals(amounts("5.00"), commission);
    }

    @Test
    void commissionVariantsAddUp() {
        // given
        billingReturns(entry("SUC", "-5.00", "offer-1"), entry("FSF", "-1.50", "offer-1"),
                entry("USF", "-0.50", "offer-1"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"));

        // then
        assertEquals(amounts("7.00"), commission);
    }

    @Test
    void entryWithoutOfferGoesToFirstLine() {
        // given
        billingReturns(entry("SUC", "-3.00", null));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then
        assertEquals(amounts("3.00", "0.00"), commission);
    }

    @Test
    void entryForOfferNotOnTheOrderGoesToFirstLine() {
        // given
        billingReturns(entry("SUC", "-4.00", "offer-9"), entry("SUC", "-1.00", "offer-2"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then
        assertEquals(amounts("4.00", "1.00"), commission);
    }

    @Test
    void sameOfferOnTwoLinesChargesFirstLineOnly() {
        // given
        billingReturns(entry("SUC", "-8.00", "offer-1"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-1"));

        // then
        assertEquals(amounts("8.00", "0.00"), commission);
    }

    @Test
    void creditEntriesReduceCommissionAndTotalNeverNegative() {
        // given: Allegro refunds commission as a positive amount of the same type
        billingReturns(entry("SUC", "-10.00", "offer-1"), entry("SUC", "4.00", "offer-1"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then
        assertEquals(amounts("6.00", "0.00"), commission);
    }

    @Test
    void refundOnAnotherLineReducesTheOrderTotal() {
        // given: the refund names another offer of the order than the charge
        billingReturns(entry("SUC", "-10.00", "offer-1"), entry("SUC", "4.00", "offer-2"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then: the order's commission is 6.00, not 10.00
        assertEquals(amounts("6.00", "0.00"), commission);
    }

    @Test
    void refundAboveChargeGivesZero() {
        // given
        billingReturns(entry("SUC", "4.00", "offer-2"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then
        assertEquals(amounts("0.00", "0.00"), commission);
    }

    @Test
    void pagingStopsAfterTenFullPages() {
        // given: an endpoint that keeps answering with full pages, whatever the offset
        List<AllegroBillingEntriesResponse.Entry> fullPage = new ArrayList<>();
        IntStream.range(0, 99).forEach(i -> fullPage.add(entry("LIS", "-0.01", "offer-1")));
        fullPage.add(entry("SUC", "-1.00", "offer-1"));
        when(restApi.fetchWithAuthRetry(eq("/billing/billing-entries"), anyMap(), eq(AllegroBillingEntriesResponse.class)))
                .thenReturn(new AllegroBillingEntriesResponse(fullPage));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"));

        // then: what was read counts, and the import is not held up
        assertEquals(amounts("10.00"), commission);
        verify(restApi, times(10)).fetchWithAuthRetry(eq("/billing/billing-entries"), anyMap(),
                eq(AllegroBillingEntriesResponse.class));
    }

    @Test
    void entriesOfAnotherOrderAreIgnored() {
        // given
        AllegroBillingEntriesResponse.Entry other = new AllegroBillingEntriesResponse.Entry("e-x",
                new AllegroBillingEntriesResponse.Type("SUC", "SUC"), new AllegroBillingEntriesResponse.Amount("-9.00", "PLN"),
                "DEBIT", null, new AllegroBillingEntriesResponse.Offer("offer-1"),
                new AllegroBillingEntriesResponse.Order("another-order"));
        billingReturns(entry("SUC", "-2.00", "offer-1"), other);

        // when
        List<BigDecimal> commission = forLines(line("offer-1"));

        // then
        assertEquals(amounts("2.00"), commission);
    }

    @Test
    void nullResponseGivesZero() {
        // given: an unstubbed mock returns null

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then
        assertEquals(amounts("0.00", "0.00"), commission);
    }

    @Test
    void emptyEntriesGiveZero() {
        // given
        billingReturns();

        // when
        List<BigDecimal> commission = forLines(line("offer-1"), line("offer-2"));

        // then
        assertEquals(amounts("0.00", "0.00"), commission);
    }

    @Test
    void httpErrorGivesZero() {
        // given: the shop's token lacks allegro:api:billing:read
        when(restApi.fetchWithAuthRetry(eq("/billing/billing-entries"), anyMap(), eq(AllegroBillingEntriesResponse.class)))
                .thenThrow(new HttpClientException(403, "{\"errors\":[{\"code\":\"AccessDenied\"}]}"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"));

        // then
        assertEquals(amounts("0.00"), commission);
    }

    @Test
    void unparsableAmountGivesZero() {
        // given
        billingReturns(entry("SUC", "n/a", "offer-1"));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"));

        // then
        assertEquals(amounts("0.00"), commission);
    }

    @Test
    void fetchesNextPageWhenPageIsFull() {
        // given
        List<AllegroBillingEntriesResponse.Entry> fullPage = new ArrayList<>();
        IntStream.range(0, 100).forEach(i -> fullPage.add(entry("LIS", "-0.01", "offer-1")));
        when(restApi.fetchWithAuthRetry(eq("/billing/billing-entries"), argThat(p -> p != null && "0".equals(p.get("offset"))),
                eq(AllegroBillingEntriesResponse.class)))
                .thenReturn(new AllegroBillingEntriesResponse(fullPage));
        when(restApi.fetchWithAuthRetry(eq("/billing/billing-entries"), argThat(p -> p != null && "100".equals(p.get("offset"))),
                eq(AllegroBillingEntriesResponse.class)))
                .thenReturn(new AllegroBillingEntriesResponse(List.of(entry("SUC", "-2.00", "offer-1"))));

        // when
        List<BigDecimal> commission = forLines(line("offer-1"));

        // then
        assertEquals(amounts("2.00"), commission);
    }
}
