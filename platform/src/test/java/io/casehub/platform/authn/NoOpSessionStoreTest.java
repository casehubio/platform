package io.casehub.platform.authn;

import io.casehub.platform.api.authn.SessionStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpSessionStoreTest {

    private final SessionStore store = new NoOpSessionStore();

    @Test
    void findByIdReturnsEmpty() {
        assertThat(store.findById("session-1")).isEmpty();
    }

    @Test
    void deleteIsNoOp() {
        store.delete("session-1");
    }

    @Test
    void deleteByActorIdIsNoOp() {
        store.deleteByActorId("actor-1", "tenant-1");
    }
}
