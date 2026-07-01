package io.casehub.platform.identity;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScimActorDIDProvider graceful handling of unconfigured SCIM.
 * isConfigured() checks only endpoint presence — authToken validation happens
 * on explicit validate() call. didFor() returns empty when endpoint is blank.
 */
class ScimActorDIDProviderValidationTest {

    @Test
    void didFor_returns_empty_when_endpoint_is_blank() {
        var lookup = new ScimAgentLookup(
                "", "valid-token", 1000, Duration.ofMinutes(1), true);
        var provider = new ScimActorDIDProvider(lookup);
        assertTrue(provider.didFor("claude:reviewer@v1").isEmpty(),
                "Blank endpoint must return empty — isConfigured() returns false");
    }

    @Test
    void lookup_validate_throws_when_authToken_is_blank() {
        // validate() is still available on ScimAgentLookup for explicit checks
        var lookup = new ScimAgentLookup(
                "https://scim.example.com", "", 1000, Duration.ofMinutes(1), true);
        var ex = assertThrows(IllegalArgumentException.class, lookup::validate,
                "Blank authToken must throw on explicit validate() call");
        assertTrue(ex.getMessage().contains("auth-token"),
                "Exception message must mention auth-token, got: " + ex.getMessage());
    }

    @Test
    void lookup_validate_throws_when_endpoint_is_blank() {
        var lookup = new ScimAgentLookup(
                "", "valid-token", 1000, Duration.ofMinutes(1), true);
        assertThrows(IllegalArgumentException.class, lookup::validate,
                "Blank endpoint must throw on explicit validate() call");
    }

    @Test
    void lookup_validate_enforces_https_when_requireHttps_true() {
        var lookup = new ScimAgentLookup(
                "http://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), true);
        var ex = assertThrows(IllegalArgumentException.class, lookup::validate,
                "requireHttps=true with http:// endpoint must throw on explicit validate() call");
        assertTrue(ex.getMessage().contains("HTTPS"),
                "Exception message must mention HTTPS, got: " + ex.getMessage());
    }

    @Test
    void lookup_validate_allows_http_when_requireHttps_false() {
        var lookup = new ScimAgentLookup(
                "http://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), false);
        assertDoesNotThrow(lookup::validate,
                "requireHttps=false with http:// endpoint must not throw on explicit validate() call");
    }

    @Test
    void lookup_validate_passes_https_when_requireHttps_true() {
        var lookup = new ScimAgentLookup(
                "https://scim.example.com", "secret-token", 1000, Duration.ofMinutes(1), true);
        assertDoesNotThrow(lookup::validate,
                "requireHttps=true with https:// endpoint must not throw on explicit validate() call");
    }
}
