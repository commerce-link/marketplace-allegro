package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.commercelink.marketplace.api.MarketplaceOffer;
import pl.commercelink.rest.client.RestApi;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;

@EnabledIfSystemProperty(named = "allegro.sandbox.smoke", matches = "true")
class AllegroOfferExportSandboxSmokeTest {

    private static final String API_URL = "https://api.allegro.pl.allegrosandbox.pl";
    private static final String TOKEN_URL = "https://allegro.pl.allegrosandbox.pl/auth/oauth/token";

    @Test
    void exportsSingleOfferAgainstSandbox() throws Exception {
        // given
        String accessToken = fetchAccessToken(
                System.getenv("ALLEGRO_CLIENT_ID"),
                System.getenv("ALLEGRO_CLIENT_SECRET"),
                System.getenv("ALLEGRO_REFRESH_TOKEN"));
        RestApi restApi = RestApi.builder(API_URL)
                .defaultHeader("Accept", "application/vnd.allegro.public.v1+json")
                .defaultHeader("Content-Type", "application/vnd.allegro.public.v1+json")
                .build();
        AllegroMarketplaceProvider provider = new AllegroMarketplaceProvider(
                new RestApiWithRetry(restApi, () -> accessToken));
        String smokeEan = System.getenv("ALLEGRO_SMOKE_EAN");
        if (smokeEan == null || smokeEan.isBlank()) {
            // when / then
            provider.exportOffers(List.of(), List.of());
            System.out.println("Read-only smoke OK (listing + brak zmian); ustaw ALLEGRO_SMOKE_EAN aby przetestowac create");
            return;
        }
        MarketplaceOffer offer = new MarketplaceOffer(
                "SMOKE-" + smokeEan, smokeEan, null, null, null, null, 9999L, 1L, 3);

        // when
        provider.exportOffers(List.of(offer), List.of());
        provider.exportOffers(List.of(), List.of(new MarketplaceOffer(
                "SMOKE-" + smokeEan, null, null, null, null, null, 9999L, 0L, 0)));

        // then
        System.out.println("Smoke OK: create + end dla EAN " + smokeEan);
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
