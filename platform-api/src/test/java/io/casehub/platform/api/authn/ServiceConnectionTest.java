package io.casehub.platform.api.authn;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ServiceConnectionTest {

    @Test
    void serviceConnectionRecordFields() {
        var conn = new ServiceConnection("actor1", "google", "tenant1",
            ServiceConnectionStatus.CONNECTED,
            Set.of("openid", "email"), Set.of(), Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals("actor1", conn.actorId());
        assertEquals("google", conn.provider());
        assertEquals(ServiceConnectionStatus.CONNECTED, conn.status());
        assertTrue(conn.missingScopes().isEmpty());
    }

    @Test
    void serviceAccessTokenRecordFields() {
        var token = new ServiceAccessToken("abc123",
            Instant.parse("2026-01-01T01:00:00Z"),
            Set.of("openid", "drive.readonly"));
        assertEquals("abc123", token.accessToken());
        assertEquals(2, token.grantedScopes().size());
    }

    @Test
    void serviceConnectionExceptionCarriesScopeContext() {
        var ex = new ServiceConnectionException("No connection",
            "google", "actor1",
            Set.of("drive.readonly"), Set.of("openid"), Set.of("drive.readonly"));
        assertEquals("google", ex.provider());
        assertEquals("actor1", ex.actorId());
        assertEquals(Set.of("drive.readonly"), ex.requiredScopes());
        assertEquals(Set.of("openid"), ex.grantedScopes());
        assertEquals(Set.of("drive.readonly"), ex.missingScopes());
    }

    @Test
    void serviceConnectionStatusValues() {
        assertEquals(3, ServiceConnectionStatus.values().length);
        assertNotNull(ServiceConnectionStatus.valueOf("CONNECTED"));
        assertNotNull(ServiceConnectionStatus.valueOf("PARTIAL"));
        assertNotNull(ServiceConnectionStatus.valueOf("DISCONNECTED"));
    }

    @Test
    void scopeRegistryDefaultRegisteredProviders() {
        ScopeRegistry registry = new ScopeRegistry() {
            @Override public void register(String p, Set<String> s, Class<?> c) {}
            @Override public Set<String> requiredScopes(String p) { return Set.of(); }
            @Override public Set<String> requiredScopes(String p, Class<?> c) { return Set.of(); }
            @Override public boolean satisfies(String p, Set<String> s) { return true; }
            @Override public Set<String> missingScopes(String p, Set<String> s) { return Set.of(); }
        };
        assertTrue(registry.registeredProviders().isEmpty());
    }

    @Test
    void serviceConnectionProviderDisconnectDefaultThrows() {
        ServiceConnectionProvider provider = new ServiceConnectionProvider() {
            @Override public ServiceConnection getConnection(String a, String p, String t) { return null; }
            @Override public List<ServiceConnection> listConnections(String a, String t) { return List.of(); }
            @Override public ServiceAccessToken getAccessToken(String a, String p, String t) { return null; }
            @Override public Set<String> missingScopes(String a, String p, String t) { return Set.of(); }
        };
        assertThrows(UnsupportedOperationException.class,
            () -> provider.disconnect("actor1", "google", "tenant1"));
    }
}
