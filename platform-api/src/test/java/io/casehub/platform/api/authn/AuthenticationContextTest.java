package io.casehub.platform.api.authn;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationContextTest {

    @Test
    void constructor_rejects_null_method() {
        assertThrows(NullPointerException.class,
            () -> new AuthenticationContext(null, "tenant", "https://example.com", Optional.empty(), Map.of()));
    }

    @Test
    void constructor_rejects_null_tenancyId() {
        assertThrows(NullPointerException.class,
            () -> new AuthenticationContext("webauthn", null, "https://example.com", Optional.empty(), Map.of()));
    }

    @Test
    void constructor_rejects_null_origin() {
        assertThrows(NullPointerException.class,
            () -> new AuthenticationContext("webauthn", "tenant", null, Optional.empty(), Map.of()));
    }

    @Test
    void valid_construction() {
        var ctx = new AuthenticationContext("webauthn", "tenant", "https://example.com", Optional.empty(), Map.of());
        assertEquals("webauthn", ctx.method());
        assertEquals("tenant", ctx.tenancyId());
        assertTrue(ctx.existingPrincipal().isEmpty());
    }
}
