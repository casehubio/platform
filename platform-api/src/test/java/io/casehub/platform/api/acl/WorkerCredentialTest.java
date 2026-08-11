package io.casehub.platform.api.acl;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkerCredentialTest {

    private static final WorkerAction READ =
        new WorkerAction("READ_CONTEXT", AclAction.READ);

    @Test
    void constructsWithResourceId() {
        var rid = new ResourceId("case", "abc");
        var cred = new WorkerCredential("tok", "actor", rid, "tenant",
            Set.of(READ), Instant.now().plusSeconds(60), Instant.now());
        assertEquals(rid, cred.resourceId());
    }

    @Test
    void isExpired_returnsTrueWhenPastExpiry() {
        var cred = new WorkerCredential("tok", "actor",
            new ResourceId("case", "abc"), "tenant",
            Set.of(READ), Instant.now().minusSeconds(60), Instant.now().minusSeconds(120));
        assertTrue(cred.isExpired());
    }

    @Test
    void isExpired_returnsFalseWhenBeforeExpiry() {
        var cred = new WorkerCredential("tok", "actor",
            new ResourceId("case", "abc"), "tenant",
            Set.of(READ), Instant.now().plusSeconds(3600), Instant.now());
        assertFalse(cred.isExpired());
    }
}
