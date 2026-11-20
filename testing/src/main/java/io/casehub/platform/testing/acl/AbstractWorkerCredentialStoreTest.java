package io.casehub.platform.testing.acl;

import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.acl.WorkerAction;
import io.casehub.platform.api.acl.WorkerCredential;
import io.casehub.platform.api.acl.WorkerCredentialStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractWorkerCredentialStoreTest {

    private static final WorkerAction READ_CONTEXT =
            new WorkerAction("read-context", AclAction.READ);

    protected WorkerCredentialStore store;

    protected abstract WorkerCredentialStore createStore();

    @BeforeEach
    void setUp() {
        store = createStore();
    }

    @Test
    void storeAndLookup() {
        var cred = credential("t1", "actor-1", new ResourceId("case", "c1"), "tenant-1");
        store.store(cred);

        var result = store.lookup("t1");

        assertTrue(result.isPresent());
        assertEquals("actor-1", result.get().actorId());
        assertEquals("tenant-1", result.get().tenancyId());
    }

    @Test
    void lookupUnknownTokenReturnsEmpty() {
        assertTrue(store.lookup("nonexistent").isEmpty());
    }

    @Test
    void storePreservesAllFields() {
        var rid = new ResourceId("case", "c1");
        var actions = Set.of(READ_CONTEXT);
        var expiresAt = Instant.now().plusSeconds(3600);
        var createdAt = Instant.now();
        var cred = new WorkerCredential("t1", "actor-1", rid, "tenant-1",
                actions, expiresAt, createdAt);

        store.store(cred);
        var result = store.lookup("t1").orElseThrow();

        assertEquals("t1", result.token());
        assertEquals("actor-1", result.actorId());
        assertEquals(rid, result.resourceId());
        assertEquals("tenant-1", result.tenancyId());
        assertEquals(actions, result.actions());
        assertEquals(expiresAt, result.expiresAt());
        assertEquals(createdAt, result.createdAt());
    }

    @Test
    void revokeRemovesCredential() {
        store.store(credential("t1", "actor-1", new ResourceId("case", "c1"), "tenant-1"));

        store.revoke("t1");

        assertTrue(store.lookup("t1").isEmpty());
    }

    @Test
    void revokeNonexistentTokenIsNoOp() {
        store.revoke("nonexistent");
    }

    @Test
    void revokeByResourceSweepsAllForResource() {
        var rid = new ResourceId("case", "c1");
        store.store(credential("t1", "actor-1", rid, "tenant-1"));
        store.store(credential("t2", "actor-2", rid, "tenant-1"));
        store.store(credential("t3", "actor-3", new ResourceId("case", "c2"), "tenant-1"));

        var revoked = store.revokeByResource(rid);

        assertEquals(2, revoked.size());
        assertTrue(store.lookup("t1").isEmpty());
        assertTrue(store.lookup("t2").isEmpty());
        assertTrue(store.lookup("t3").isPresent());
    }

    @Test
    void revokeByResourceReturnsEmptyForUnknownResource() {
        var revoked = store.revokeByResource(new ResourceId("case", "unknown"));
        assertTrue(revoked.isEmpty());
    }

    @Test
    void revokeByActorSweepsAllForActor() {
        store.store(credential("t1", "agent:pool", new ResourceId("case", "c1"), "tenant-1"));
        store.store(credential("t2", "agent:pool", new ResourceId("case", "c2"), "tenant-1"));
        store.store(credential("t3", "agent:other", new ResourceId("case", "c3"), "tenant-1"));

        var revoked = store.revokeByActor("agent:pool");

        assertEquals(2, revoked.size());
        assertTrue(store.lookup("t1").isEmpty());
        assertTrue(store.lookup("t2").isEmpty());
        assertTrue(store.lookup("t3").isPresent());
    }

    @Test
    void revokeByActorReturnsEmptyForUnknownActor() {
        var revoked = store.revokeByActor("unknown-actor");
        assertTrue(revoked.isEmpty());
    }

    @Test
    void findActiveByActorAndResourceExcludesExpired() {
        var rid = new ResourceId("case", "c1");
        store.store(credential("t1", "agent:pool", rid, "tenant-1"));
        store.store(new WorkerCredential("t2", "agent:pool", rid, "tenant-1",
                Set.of(READ_CONTEXT), Instant.now().minusSeconds(60), Instant.now().minusSeconds(120)));

        var active = store.findActiveByActorAndResource("agent:pool", rid);

        assertEquals(1, active.size());
        assertEquals("t1", active.get(0).token());
    }

    @Test
    void findActiveByActorAndResourceReturnsEmptyWhenNoneMatch() {
        store.store(credential("t1", "actor-1", new ResourceId("case", "c1"), "tenant-1"));

        var active = store.findActiveByActorAndResource("actor-1", new ResourceId("case", "c2"));

        assertTrue(active.isEmpty());
    }

    @Test
    void findActiveByActorAndResourceFiltersOtherActors() {
        var rid = new ResourceId("case", "c1");
        store.store(credential("t1", "actor-1", rid, "tenant-1"));
        store.store(credential("t2", "actor-2", rid, "tenant-1"));

        var active = store.findActiveByActorAndResource("actor-1", rid);

        assertEquals(1, active.size());
        assertEquals("t1", active.get(0).token());
    }

    protected WorkerCredential credential(String token, String actorId,
                                           ResourceId resourceId, String tenancyId) {
        return new WorkerCredential(token, actorId, resourceId, tenancyId,
                Set.of(READ_CONTEXT), Instant.now().plusSeconds(3600), Instant.now());
    }
}
