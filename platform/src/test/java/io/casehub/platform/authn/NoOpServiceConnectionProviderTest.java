package io.casehub.platform.authn;

import io.casehub.platform.api.authn.ServiceConnectionException;
import io.casehub.platform.api.authn.ServiceConnectionStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoOpServiceConnectionProviderTest {

    private final NoOpServiceConnectionProvider provider = new NoOpServiceConnectionProvider();

    @Test
    void getConnectionReturnsDisconnected() {
        var conn = provider.getConnection("actor1", "google", "tenant1");
        assertEquals(ServiceConnectionStatus.DISCONNECTED, conn.status());
        assertEquals("actor1", conn.actorId());
        assertEquals("google", conn.provider());
        assertTrue(conn.grantedScopes().isEmpty());
        assertTrue(conn.missingScopes().isEmpty());
        assertNull(conn.connectedAt());
    }

    @Test
    void listConnectionsReturnsEmpty() {
        assertTrue(provider.listConnections("actor1", "tenant1").isEmpty());
    }

    @Test
    void getAccessTokenThrows() {
        assertThrows(ServiceConnectionException.class,
            () -> provider.getAccessToken("actor1", "google", "tenant1"));
    }

    @Test
    void disconnectNoOps() {
        assertDoesNotThrow(() -> provider.disconnect("actor1", "google", "tenant1"));
    }

    @Test
    void missingScopesReturnsEmpty() {
        assertTrue(provider.missingScopes("actor1", "google", "tenant1").isEmpty());
    }
}
