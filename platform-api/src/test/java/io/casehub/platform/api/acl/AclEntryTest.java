package io.casehub.platform.api.acl;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class AclEntryTest {

    @Test
    void isExpired_nullExpiresAt_returnsFalse() {
        var entry = new AclEntry("actor1", "case:abc", AclAction.READ,
                Instant.now(), null, "tenant1");
        assertFalse(entry.isExpired());
    }

    @Test
    void isExpired_futureExpiresAt_returnsFalse() {
        var entry = new AclEntry("actor1", "case:abc", AclAction.READ,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), "tenant1");
        assertFalse(entry.isExpired());
    }

    @Test
    void isExpired_pastExpiresAt_returnsTrue() {
        var entry = new AclEntry("actor1", "case:abc", AclAction.READ,
                Instant.now(), Instant.now().minus(1, ChronoUnit.HOURS), "tenant1");
        assertTrue(entry.isExpired());
    }

    @Test
    void recordComponents_areAccessible() {
        var now = Instant.now();
        var expires = now.plus(1, ChronoUnit.DAYS);
        var entry = new AclEntry("actor1", "case:abc", AclAction.WRITE, now, expires, "tenant1");

        assertEquals("actor1", entry.actorId());
        assertEquals("case:abc", entry.resourceId());
        assertEquals(AclAction.WRITE, entry.action());
        assertEquals(now, entry.grantedAt());
        assertEquals(expires, entry.expiresAt());
        assertEquals("tenant1", entry.tenancyId());
    }
}
