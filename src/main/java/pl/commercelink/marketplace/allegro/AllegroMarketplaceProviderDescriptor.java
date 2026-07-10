package pl.commercelink.marketplace.allegro;

import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.List;
import java.util.Map;

public class AllegroMarketplaceProviderDescriptor implements MarketplaceProviderDescriptor {

    static final String DEFAULT_API_URL = "https://api.allegro.pl";
    static final String DEFAULT_TOKEN_URL = "https://allegro.pl/auth/oauth/token";
    static final String ACCEPT_HEADER = "application/vnd.allegro.public.v1+json";

    @Override
    public String name() {
        return "Allegro";
    }

    @Override
    public String displayName() {
        return "Allegro";
    }

    @Override
    public List<ProviderField> configurationFields() {
        return List.of(
                new ProviderField("clientId", "Client ID", ProviderField.FieldType.TEXT, true, "Client ID"),
                new ProviderField("clientSecret", "Client Secret", ProviderField.FieldType.PASSWORD, true, "******"),
                new ProviderField("refreshToken", "Refresh Token", ProviderField.FieldType.PASSWORD, true, "******"));
    }

    @Override
    public MarketplaceProvider create(Map<String, String> configuration) {
        throw new UnsupportedOperationException("Use create(configuration, context) instead");
    }

    @Override
    public MarketplaceProvider create(Map<String, String> configuration, Map<String, Object> context) {
        RestApiWithRetry restApi = (RestApiWithRetry) context.get("restApi");
        return new AllegroMarketplaceProvider(restApi);
    }

    @Override
    public AuthConfig authConfig() {
        String tokenUrl = resolve("ALLEGRO_TOKEN_URL", DEFAULT_TOKEN_URL);
        return new AuthConfig.OAuth2(
                resolve("ALLEGRO_API_URL", DEFAULT_API_URL),
                tokenUrl,
                tokenUrl,
                90L * 24 * 60 * 60,
                ACCEPT_HEADER,
                "refreshToken");
    }

    private static String resolve(String key, String defaultValue) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(key);
        return env != null && !env.isBlank() ? env : defaultValue;
    }
}
