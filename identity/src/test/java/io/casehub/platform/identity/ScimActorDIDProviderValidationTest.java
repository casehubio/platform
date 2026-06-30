package io.casehub.platform.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScimActorDIDProvider.validateEndpoint() — no CDI required.
 * After refactor, ScimActorDIDProvider wraps ScimAgentLookup. Tests now construct
 * the lookup first, then wrap it.
 */
class ScimActorDIDProviderValidationTest {

    @Test
    void testConstructorAllowsHttpEndpoints() {
        // The test constructor is documented for use with http:// WireMock endpoints.
        // requireHttps must be false so validateEndpoint() does not reject http://.
        var lookup = new ScimAgentLookup(
                "http://localhost:8080", "secret-token", 1000, Duration.ofMinutes(1), false);
        var provider = new ScimActorDIDProvider(lookup);
        assertDoesNotThrow(provider::validateEndpoint,
                "Test constructor with http:// must not throw — requireHttps must be false");
    }

    @Test
    void validateEndpoint_throws_when_authToken_is_blank() {
        var lookup = new ScimAgentLookup(
                "https://scim.example.com", "", 1000, Duration.ofMinutes(1), true);
        var provider = new ScimActorDIDProvider(lookup);
        var ex = assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "Blank authToken with configured endpoint must throw");
        assertTrue(ex.getMessage().contains("auth-token"),
                "Exception message must mention auth-token, got: " + ex.getMessage());
    }

    @Test
    void validateEndpoint_throws_when_authToken_is_whitespace_only() {
        var lookup = new ScimAgentLookup(
                "https://scim.example.com", "   ", 1000, Duration.ofMinutes(1), true);
        var provider = new ScimActorDIDProvider(lookup);
        var ex = assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "Whitespace-only authToken must throw");
        assertTrue(ex.getMessage().contains("auth-token"),
                "Exception message must mention auth-token, got: " + ex.getMessage());
    }

    @Test
    void validateEndpoint_passes_when_https_endpoint_and_non_blank_authToken() {
        var lookup = new ScimAgentLookup(
                "https://scim.example.com", "valid-token", 1000, Duration.ofMinutes(1), true);
        var provider = new ScimActorDIDProvider(lookup);
        assertDoesNotThrow(provider::validateEndpoint);
    }

    @Test
    void validateEndpoint_throws_when_endpoint_is_blank() {
        var lookup = new ScimAgentLookup(
                "", "valid-token", 1000, Duration.ofMinutes(1), true);
        var provider = new ScimActorDIDProvider(lookup);
        assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "Blank endpoint must throw regardless of authToken");
    }

    @Test
    void fiveArgConstructor_enforces_https_when_requireHttps_true() {
        // 5-arg constructor with requireHttps=true — http:// must be rejected
        var lookup = new ScimAgentLookup(
                "http://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), true);
        var provider = new ScimActorDIDProvider(lookup);
        var ex = assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "requireHttps=true with http:// endpoint must throw");
        assertTrue(ex.getMessage().contains("HTTPS"),
                "Exception message must mention HTTPS, got: " + ex.getMessage());
    }

    @Test
    void fiveArgConstructor_allows_http_when_requireHttps_false() {
        // 5-arg constructor with requireHttps=false — http:// must be accepted
        var lookup = new ScimAgentLookup(
                "http://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), false);
        var provider = new ScimActorDIDProvider(lookup);
        assertDoesNotThrow(provider::validateEndpoint,
                "requireHttps=false with http:// endpoint must not throw");
    }

    @Test
    void fiveArgConstructor_passes_https_when_requireHttps_true() {
        var lookup = new ScimAgentLookup(
                "https://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), true);
        var provider = new ScimActorDIDProvider(lookup);
        assertDoesNotThrow(provider::validateEndpoint,
                "requireHttps=true with https:// endpoint must not throw");
    }
}
