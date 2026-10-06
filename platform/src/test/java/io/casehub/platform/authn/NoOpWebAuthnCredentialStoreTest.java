package io.casehub.platform.authn;

import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NoOpWebAuthnCredentialStoreTest {

    private final WebAuthnCredentialStore store = new NoOpWebAuthnCredentialStore();

    @Test
    void findByCredentialIdReturnsEmpty() {
        assertThat(store.findByCredentialId("cred-1")).isEmpty();
    }

    @Test
    void findByActorIdReturnsEmptyList() {
        assertThat(store.findByActorId("actor-1", "tenant-1")).isEmpty();
    }

    @Test
    void updateAfterAuthenticationIsNoOp() {
        store.updateAfterAuthentication("cred-1", 42, Instant.now());
    }

    @Test
    void deleteIsNoOp() {
        store.delete("cred-1");
    }
}
