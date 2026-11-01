package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.PasswordCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryPasswordCredentialStoreTest {

    private InMemoryPasswordCredentialStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryPasswordCredentialStore();
    }

    @Test
    void storeAndFind() {
        var cred = new PasswordCredential("u1", "t1", "$argon2id$hash", Instant.now(), null);
        store.store(cred);
        var found = store.findByActorId("u1", "t1");
        assertThat(found).isPresent().contains(cred);
    }

    @Test
    void findMissingReturnsEmpty() {
        assertThat(store.findByActorId("missing", "t1")).isEmpty();
    }

    @Test
    void updatePasswordChangesHash() {
        store.store(new PasswordCredential("u1", "t1", "old-hash", Instant.now(), null));
        store.updatePassword("u1", "t1", "new-hash");
        var found = store.findByActorId("u1", "t1");
        assertThat(found).isPresent();
        assertThat(found.get().hashedPassword()).isEqualTo("new-hash");
    }

    @Test
    void updateLastUsedSetsTimestamp() {
        store.store(new PasswordCredential("u1", "t1", "hash", Instant.now(), null));
        store.updateLastUsed("u1", "t1");
        var found = store.findByActorId("u1", "t1");
        assertThat(found).isPresent();
        assertThat(found.get().lastUsedAt()).isNotNull();
    }

    @Test
    void deleteRemoves() {
        store.store(new PasswordCredential("u1", "t1", "hash", Instant.now(), null));
        store.delete("u1", "t1");
        assertThat(store.findByActorId("u1", "t1")).isEmpty();
    }
}
