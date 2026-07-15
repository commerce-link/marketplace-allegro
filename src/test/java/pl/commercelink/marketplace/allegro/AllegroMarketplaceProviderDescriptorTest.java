package pl.commercelink.marketplace.allegro;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.marketplace.api.MarketplaceProvider;
import pl.commercelink.marketplace.api.MarketplaceProviderDescriptor;
import pl.commercelink.provider.api.AuthConfig;
import pl.commercelink.provider.api.ProviderField;
import pl.commercelink.rest.client.RestApi;
import pl.commercelink.rest.client.RestApiWithRetry;

import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class AllegroMarketplaceProviderDescriptorTest {

    @AfterEach
    void clearOverrides() {
        System.clearProperty("ALLEGRO_API_URL");
        System.clearProperty("ALLEGRO_TOKEN_URL");
    }

    @Test
    void isDiscoverableViaServiceLoader() {
        // when
        boolean found = ServiceLoader.load(MarketplaceProviderDescriptor.class).stream()
                .anyMatch(p -> p.type() == AllegroMarketplaceProviderDescriptor.class);

        // then
        assertTrue(found);
    }

    @Test
    void exposesAllegroIdentity() {
        // given
        AllegroMarketplaceProviderDescriptor descriptor = new AllegroMarketplaceProviderDescriptor();

        // when / then
        assertEquals("Allegro", descriptor.name());
        assertEquals("Allegro", descriptor.displayName());
    }

    @Test
    void exposesClientIdSecretAndRefreshTokenFields() {
        // given
        AllegroMarketplaceProviderDescriptor descriptor = new AllegroMarketplaceProviderDescriptor();

        // when
        List<ProviderField> fields = descriptor.configurationFields();

        // then
        assertEquals(3, fields.size());
        assertEquals("clientId", fields.get(0).key());
        assertEquals(ProviderField.FieldType.TEXT, fields.get(0).type());
        assertTrue(fields.get(0).required());
        assertEquals("clientSecret", fields.get(1).key());
        assertEquals(ProviderField.FieldType.PASSWORD, fields.get(1).type());
        assertEquals("refreshToken", fields.get(2).key());
        assertEquals(ProviderField.FieldType.PASSWORD, fields.get(2).type());
    }

    @Test
    void authConfigDefaultsToProductionUrls() {
        // given
        AllegroMarketplaceProviderDescriptor descriptor = new AllegroMarketplaceProviderDescriptor();

        // when
        AuthConfig.OAuth2 authConfig = (AuthConfig.OAuth2) descriptor.authConfig();

        // then
        assertEquals("https://api.allegro.pl", authConfig.apiUrl());
        assertEquals("https://allegro.pl/auth/oauth/token", authConfig.authEndpointPath());
        assertEquals("https://allegro.pl/auth/oauth/token", authConfig.refreshEndpointPath());
        assertEquals(90L * 24 * 60 * 60, authConfig.refreshTokenExpirationSeconds());
        assertEquals("application/vnd.allegro.public.v1+json", authConfig.acceptHeader());
        assertEquals("refreshToken", authConfig.refreshTokenFieldKey());
        assertEquals(AllegroMarketplaceProviderDescriptor.ACCEPT_HEADER, authConfig.contentTypeHeader());
    }

    @Test
    void authConfigRespectsSandboxOverrides() {
        // given
        System.setProperty("ALLEGRO_API_URL", "https://api.allegro.pl.allegrosandbox.pl");
        System.setProperty("ALLEGRO_TOKEN_URL", "https://allegro.pl.allegrosandbox.pl/auth/oauth/token");
        AllegroMarketplaceProviderDescriptor descriptor = new AllegroMarketplaceProviderDescriptor();

        // when
        AuthConfig.OAuth2 authConfig = (AuthConfig.OAuth2) descriptor.authConfig();

        // then
        assertEquals("https://api.allegro.pl.allegrosandbox.pl", authConfig.apiUrl());
        assertEquals("https://allegro.pl.allegrosandbox.pl/auth/oauth/token", authConfig.authEndpointPath());
    }

    @Test
    void createRequiresContext() {
        // given
        AllegroMarketplaceProviderDescriptor descriptor = new AllegroMarketplaceProviderDescriptor();

        // when / then
        assertThrows(UnsupportedOperationException.class, () -> descriptor.create(Map.of()));
    }

    @Test
    void createBuildsProviderFromContextRestApi() {
        // given
        AllegroMarketplaceProviderDescriptor descriptor = new AllegroMarketplaceProviderDescriptor();
        RestApiWithRetry restApi = new RestApiWithRetry(
                RestApi.builder("https://api.allegro.pl").build(), () -> "token");

        // when
        MarketplaceProvider provider = descriptor.create(Map.of(), Map.of("restApi", restApi));

        // then
        assertNotNull(provider);
    }
}
