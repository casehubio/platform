package io.casehub.platform.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScimActorDIDProvider.validateEndpoint() — no CDI required.
 * Uses the package-private test constructor to create instances directly.
 */
class ScimActorDIDProviderValidationTest {

    // ── Fix 1: test constructor must have requireHttps = false ────────────────

    @Test
    void testConstructorAllowsHttpEndpoints() {
        // The test constructor is documented for use with http:// WireMock endpoints.
        // requireHttps must be false so validateEndpoint() does not reject http://.
        var provider = new ScimActorDIDProvider(
                "http://localhost:8080", "secret-token", 1000, Duration.ofMinutes(1));
        assertDoesNotThrow(provider::validateEndpoint,
                "Test constructor with http:// must not throw — requireHttps must be false");
    }

    // ── Fix 2: blank authToken must fail at validateEndpoint() ────────────────

    @Test
    void validateEndpoint_throws_when_authToken_is_blank() {
        var provider = new ScimActorDIDProvider(
                "https://scim.example.com", "", 1000, Duration.ofMinutes(1));
        var ex = assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "Blank authToken with configured endpoint must throw");
        assertTrue(ex.getMessage().contains("auth-token"),
                "Exception message must mention auth-token, got: " + ex.getMessage());
    }

    @Test
    void validateEndpoint_throws_when_authToken_is_whitespace_only() {
        var provider = new ScimActorDIDProvider(
                "https://scim.example.com", "   ", 1000, Duration.ofMinutes(1));
        var ex = assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "Whitespace-only authToken must throw");
        assertTrue(ex.getMessage().contains("auth-token"),
                "Exception message must mention auth-token, got: " + ex.getMessage());
    }

    @Test
    void validateEndpoint_passes_when_https_endpoint_and_non_blank_authToken() {
        var provider = new ScimActorDIDProvider(
                "https://scim.example.com", "valid-token", 1000, Duration.ofMinutes(1));
        assertDoesNotThrow(provider::validateEndpoint);
    }

    @Test
    void validateEndpoint_throws_when_endpoint_is_blank() {
        var provider = new ScimActorDIDProvider(
                "", "valid-token", 1000, Duration.ofMinutes(1));
        assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "Blank endpoint must throw regardless of authToken");
    }

    // ── 5-arg constructor: explicit requireHttps control ──────────────────────

    @Test
    void fiveArgConstructor_enforces_https_when_requireHttps_true() {
        // 5-arg constructor with requireHttps=true — http:// must be rejected
        var provider = new ScimActorDIDProvider(
                "http://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), true);
        var ex = assertThrows(IllegalArgumentException.class, provider::validateEndpoint,
                "requireHttps=true with http:// endpoint must throw");
        assertTrue(ex.getMessage().contains("HTTPS"),
                "Exception message must mention HTTPS, got: " + ex.getMessage());
    }

    @Test
    void fiveArgConstructor_allows_http_when_requireHttps_false() {
        // 5-arg constructor with requireHttps=false — http:// must be accepted
        var provider = new ScimActorDIDProvider(
                "http://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), false);
        assertDoesNotThrow(provider::validateEndpoint,
                "requireHttps=false with http:// endpoint must not throw");
    }

    @Test
    void fiveArgConstructor_passes_https_when_requireHttps_true() {
        var provider = new ScimActorDIDProvider(
                "https://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), true);
        assertDoesNotThrow(provider::validateEndpoint,
                "requireHttps=true with https:// endpoint must not throw");
    }
}
