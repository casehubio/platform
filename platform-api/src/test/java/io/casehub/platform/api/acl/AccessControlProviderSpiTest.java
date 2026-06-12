package io.casehub.platform.api.acl;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AccessControlProviderSpiTest {

    private final AccessControlProvider spi = new AccessControlProvider() {};

    @Test
    void canAccess_defaultReturnsTrue() {
        assertTrue(spi.canAccess("actor", "case:abc", AclAction.READ));
    }

    @Test
    void grant_defaultIsNoOp() {
        assertDoesNotThrow(() -> spi.grant("actor", "case:abc", AclAction.READ, Instant.now()));
    }

    @Test
    void revoke_defaultIsNoOp() {
        assertDoesNotThrow(() -> spi.revoke("actor", "case:abc", AclAction.READ));
    }

    @Test
    void revokeAll_defaultIsNoOp() {
        assertDoesNotThrow(() -> spi.revokeAll("actor", "case:abc"));
    }

    @Test
    void registerParent_defaultIsNoOp() {
        assertDoesNotThrow(() -> spi.registerParent("child:1", "parent:1"));
    }

    @Test
    void accessibleResources_defaultReturnsEmpty() {
        assertTrue(spi.accessibleResources("actor", AclResourceType.CASE, AclAction.READ).isEmpty());
    }
}
