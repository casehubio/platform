package io.casehub.platform.acl.inmem;

import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.ResourceId;
import io.casehub.platform.api.acl.WorkerAction;
import io.casehub.platform.api.acl.WorkerCredential;
import io.casehub.platform.api.acl.WorkerCredentialStore;
import io.casehub.platform.testing.acl.AbstractWorkerCredentialStoreTest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryWorkerCredentialStoreTest extends AbstractWorkerCredentialStoreTest {

    private static final WorkerAction READ_CONTEXT =
            new WorkerAction("read-context", AclAction.READ);

    @Override
    protected WorkerCredentialStore createStore() {
        return new InMemoryWorkerCredentialStore();
    }

    @Test
    void lazyEviction_expiredRemovedOnStore() {
        store.store(new WorkerCredential("old", "actor-1", new ResourceId("case", "c1"), "tenant-1",
                Set.of(READ_CONTEXT), Instant.now().minusSeconds(60), Instant.now().minusSeconds(120)));
        store.store(credential("new", "actor-2", new ResourceId("case", "c2"), "tenant-1"));

        assertTrue(store.lookup("old").isEmpty());
        assertTrue(store.lookup("new").isPresent());
    }

    @Test
    void lazyEviction_expiredRemovedOnLookup() {
        store.store(new WorkerCredential("old", "actor-1", new ResourceId("case", "c1"), "tenant-1",
                Set.of(READ_CONTEXT), Instant.now().minusSeconds(60), Instant.now().minusSeconds(120)));

        store.lookup("anything");

        assertTrue(store.lookup("old").isEmpty());
    }
}
