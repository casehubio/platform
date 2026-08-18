package io.casehub.platform.api.acl;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AclEntryTest {

    private static final ResourceId CASE_ABC = ResourceId.parse("case:abc");

    @Test
    void isExpired_nullExpiresAt_returnsFalse() {
        var entry = new AclEntry("actor1", CASE_ABC, AclAction.READ, AclEntryType.ALLOW,
                                 Instant.now(), null, "tenant1");
        assertFalse(entry.isExpired());
    }

    @Test
    void isExpired_futureExpiresAt_returnsFalse() {
        var entry = new AclEntry("actor1", CASE_ABC, AclAction.READ, AclEntryType.ALLOW,
                                 Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), "tenant1");
        assertFalse(entry.isExpired());
    }

    @Test
    void isExpired_pastExpiresAt_returnsTrue() {
        var entry = new AclEntry("actor1", CASE_ABC, AclAction.READ, AclEntryType.ALLOW,
                                 Instant.now(), Instant.now().minus(1, ChronoUnit.HOURS), "tenant1");
        assertTrue(entry.isExpired());
    }

    @Test
    void recordComponents_areAccessible() {
        var now     = Instant.now();
        var expires = now.plus(1, ChronoUnit.DAYS);
        var entry   = new AclEntry("actor1", CASE_ABC, AclAction.WRITE, AclEntryType.ALLOW, now, expires, "tenant1");

        assertEquals("actor1", entry.actorId());
        assertEquals(CASE_ABC, entry.resourceId());
        assertEquals(AclAction.WRITE, entry.action());
        assertEquals(AclEntryType.ALLOW, entry.entryType());
        assertEquals(now, entry.grantedAt());
        assertEquals(expires, entry.expiresAt());
        assertEquals("tenant1", entry.tenancyId());
    }
}
