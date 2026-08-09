package io.casehub.platform.acl.inmem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.casehub.platform.api.acl.WorkerAction;
import io.casehub.platform.api.acl.WorkerCredential;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryWorkerCredentialStoreTest {

    private InMemoryWorkerCredentialStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkerCredentialStore();
    }

    @Test
    void storeAndLookup() {
        var cred = credential("t1", "actor-1", UUID.randomUUID(), "tenant-1");
        store.store(cred);
        var result = store.lookup("t1");
        assertTrue(result.isPresent());
        assertEquals("actor-1", result.get().actorId());
    }

    @Test
    void lookup_unknownToken_returnsEmpty() {
        assertTrue(store.lookup("nonexistent").isEmpty());
    }

    @Test
    void revoke_removesCredential() {
        var cred = credential("t1", "actor-1", UUID.randomUUID(), "tenant-1");
        store.store(cred);
        store.revoke("t1");
        assertTrue(store.lookup("t1").isEmpty());
    }

    @Test
    void revokeByCase_sweepsAllForCase() {
        UUID caseId = UUID.randomUUID();
        store.store(credential("t1", "actor-1", caseId, "tenant-1"));
        store.store(credential("t2", "actor-2", caseId, "tenant-1"));
        store.store(credential("t3", "actor-3", UUID.randomUUID(), "tenant-1"));

        var revoked = store.revokeByCase(caseId);

        assertEquals(2, revoked.size());
        assertTrue(store.lookup("t1").isEmpty());
        assertTrue(store.lookup("t2").isEmpty());
        assertTrue(store.lookup("t3").isPresent());
    }

    @Test
    void revokeByActor_sweepsAllForActor() {
        store.store(credential("t1", "agent:pool", UUID.randomUUID(), "tenant-1"));
        store.store(credential("t2", "agent:pool", UUID.randomUUID(), "tenant-1"));
        store.store(credential("t3", "agent:other", UUID.randomUUID(), "tenant-1"));

        var revoked = store.revokeByActor("agent:pool");

        assertEquals(2, revoked.size());
        assertTrue(store.lookup("t1").isEmpty());
        assertTrue(store.lookup("t2").isEmpty());
        assertTrue(store.lookup("t3").isPresent());
    }

    @Test
    void findActiveByActorAndCase_excludesExpired() {
        UUID caseId = UUID.randomUUID();
        store.store(credential("t1", "agent:pool", caseId, "tenant-1"));
        store.store(new WorkerCredential("t2", "agent:pool", caseId, "tenant-1",
            Set.of(WorkerAction.READ_CONTEXT), Instant.now().minusSeconds(60), Instant.now().minusSeconds(120)));

        var active = store.findActiveByActorAndCase("agent:pool", caseId);

        assertEquals(1, active.size());
        assertEquals("t1", active.get(0).token());
    }

    @Test
    void lazyEviction_expiredRemovedOnStore() {
        store.store(new WorkerCredential("old", "actor-1", UUID.randomUUID(), "tenant-1",
            Set.of(WorkerAction.READ_CONTEXT), Instant.now().minusSeconds(60), Instant.now().minusSeconds(120)));
        store.store(credential("new", "actor-2", UUID.randomUUID(), "tenant-1"));

        assertTrue(store.lookup("old").isEmpty());
        assertTrue(store.lookup("new").isPresent());
    }

    @Test
    void lazyEviction_expiredRemovedOnLookup() {
        store.store(new WorkerCredential("old", "actor-1", UUID.randomUUID(), "tenant-1",
            Set.of(WorkerAction.READ_CONTEXT), Instant.now().minusSeconds(60), Instant.now().minusSeconds(120)));

        store.lookup("anything");

        assertTrue(store.lookup("old").isEmpty());
    }

    private WorkerCredential credential(String token, String actorId, UUID caseId, String tenancyId) {
        return new WorkerCredential(token, actorId, caseId, tenancyId,
            Set.of(WorkerAction.READ_CONTEXT), Instant.now().plusSeconds(3600), Instant.now());
    }
}
