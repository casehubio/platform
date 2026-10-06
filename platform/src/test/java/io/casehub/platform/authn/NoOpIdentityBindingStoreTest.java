package io.casehub.platform.authn;

import io.casehub.platform.api.authn.IdentityBindingStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpIdentityBindingStoreTest {

    private final IdentityBindingStore store = new NoOpIdentityBindingStore();

    @Test
    void findByExternalIdReturnsEmpty() {
        assertThat(store.findByExternalId("google", "ext-1", "tenant-1")).isEmpty();
    }

    @Test
    void findByActorIdReturnsEmpty() {
        assertThat(store.findByActorId("actor-1", "google", "tenant-1")).isEmpty();
    }

    @Test
    void unbindIsNoOp() {
        store.unbind("google", "ext-1", "tenant-1");
    }
}
