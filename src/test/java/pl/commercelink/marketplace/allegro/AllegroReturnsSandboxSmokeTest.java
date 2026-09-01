package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.commercelink.marketplace.api.MarketplaceReturn;
import pl.commercelink.marketplace.api.ReturnRefund;
import pl.commercelink.marketplace.api.ReturnRejection;
import pl.commercelink.rest.client.RestApi;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Live checks against the Allegro sandbox. Run with:
 * ALLEGRO_CLIENT_ID=... ALLEGRO_CLIENT_SECRET=... ALLEGRO_REFRESH_TOKEN=... \
 *   [ALLEGRO_SMOKE_ORDER_ID=<paid checkout-form id> ALLEGRO_SMOKE_MFN=<manufacturer code>] \
 *   [ALLEGRO_SMOKE_RETURN_ID=<customer return id>] \
 *   [ALLEGRO_SMOKE_COMMAND_ID=<fixed id, re-run to verify idempotency>] \
 *   mvn test -Dtest=AllegroReturnsSandboxSmokeTest -Dallegro.sandbox.smoke=true
 * The refund test moves (sandbox) money: it refunds 1 unit of the given line item.
 */
@EnabledIfSystemProperty(named = "allegro.sandbox.smoke", matches = "true")
class AllegroReturnsSandboxSmokeTest {

    private static final String API_URL = "https://api.allegro.pl.allegrosandbox.pl";
    private static final String TOKEN_URL = "https://allegro.pl.allegrosandbox.pl/auth/oauth/token";

    private AllegroReturns returns() throws Exception {
        String accessToken = fetchAccessToken(
                System.getenv("ALLEGRO_CLIENT_ID"),
                System.getenv("ALLEGRO_CLIENT_SECRET"),
                System.getenv("ALLEGRO_REFRESH_TOKEN"));
        RestApi restApi = RestApi.builder(API_URL)
                .defaultHeader("Accept", "application/vnd.allegro.public.v1+json")
                .defaultHeader("Content-Type", "application/vnd.allegro.public.v1+json")
                .build();
        return new AllegroReturns(new RestApiWithRetry(restApi, () -> accessToken));
    }

    @Test
    void listsCustomerReturnsFromSandbox() throws Exception {
        // when
        List<MarketplaceReturn> result = returns().fetchReturns();

        // then
        assertNotNull(result);
        System.out.println("Customer returns: " + result.size());
        result.forEach(r -> System.out.println("  " + r.externalReturnId() + " " + r.status() + " order=" + r.externalOrderId()
                + " items=" + r.items()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ALLEGRO_SMOKE_ORDER_ID", matches = ".+")
    void refundsOneUnitOfPaidSandboxOrder() throws Exception {
        // given
        String orderId = System.getenv("ALLEGRO_SMOKE_ORDER_ID");
        String mfn = System.getenv("ALLEGRO_SMOKE_MFN");
        // Reusing ALLEGRO_SMOKE_COMMAND_ID across two runs is how this test actually verifies
        // idempotency; a fresh random id every run would never exercise Allegro's deduplication.
        String commandId = Optional.ofNullable(System.getenv("ALLEGRO_SMOKE_COMMAND_ID"))
                .orElseGet(() -> UUID.randomUUID().toString());
        System.out.println("commandId=" + commandId + " (re-run with the same id to check idempotency manually)");

        // when
        returns().refundReturn(orderId, "smoke-" + commandId,
                new ReturnRefund(List.of(new ReturnRefund.Item(mfn, 1)), false, commandId, null));

        // then: no exception; verify in the sandbox panel or GET /payments/refunds?order.id=
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "ALLEGRO_SMOKE_RETURN_ID", matches = ".+")
    void rejectsSandboxReturn() throws Exception {
        // when
        returns().rejectReturn(System.getenv("ALLEGRO_SMOKE_RETURN_ID"), new ReturnRejection("Smoke test rejection"));

        // then: no exception; a second run must be a no-op (already rejected)
    }

    private String fetchAccessToken(String clientId, String clientSecret, String refreshToken) throws Exception {
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "grant_type=refresh_token&refresh_token=" + refreshToken))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        String marker = "\"access_token\":\"";
        int start = body.indexOf(marker);
        if (response.statusCode() != 200 || start == -1) {
            throw new IllegalStateException(
                    "Token refresh failed (HTTP " + response.statusCode() + "): " + body);
        }
        start += marker.length();
        return body.substring(start, body.indexOf('"', start));
    }
}
