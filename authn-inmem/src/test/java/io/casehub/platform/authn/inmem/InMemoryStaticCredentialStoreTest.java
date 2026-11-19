package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryStaticCredentialStoreTest {

    private StaticCredentialStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryStaticCredentialStore();
    }

    @Test
    void storeAndFind() {
        var record = new StaticCredentialRecord(
                "actor1", "tenant1", "google-maps",
                "AIza-fake-key", CredentialType.API_KEY,
                Instant.now(), Instant.now());

        store.store(record);
        var found = store.find("actor1", "google-maps", "tenant1");

        assertTrue(found.isPresent());
        assertEquals("AIza-fake-key", found.get().credential());
        assertEquals(CredentialType.API_KEY, found.get().type());
    }

    @Test
    void findReturnsEmptyForMissing() {
        assertTrue(store.find("actor1", "google-maps", "tenant1").isEmpty());
    }

    @Test
    void storeOverwritesExisting() {
        var record1 = new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "old-token", CredentialType.BOT_TOKEN,
                Instant.now(), Instant.now());
        var record2 = new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "new-token", CredentialType.BOT_TOKEN,
                Instant.now(), Instant.now());

        store.store(record1);
        store.store(record2);

        assertEquals("new-token",
                store.find("actor1", "slack", "tenant1").get().credential());
    }

    @Test
    void findAllReturnsByActor() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "google-maps",
                "key1", CredentialType.API_KEY, Instant.now(), Instant.now()));
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "github",
                "ghp_fake", CredentialType.PERSONAL_ACCESS_TOKEN,
                Instant.now(), Instant.now()));
        store.store(new StaticCredentialRecord(
                "actor2", "tenant1", "github",
                "ghp_other", CredentialType.PERSONAL_ACCESS_TOKEN,
                Instant.now(), Instant.now()));

        var results = store.findAll("actor1", "tenant1");
        assertEquals(2, results.size());
    }

    @Test
    void deleteRemovesRecord() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "token", CredentialType.BOT_TOKEN,
                Instant.now(), Instant.now()));

        store.delete("actor1", "slack", "tenant1");
        assertTrue(store.find("actor1", "slack", "tenant1").isEmpty());
    }

    @Test
    void deleteNonExistentIsNoOp() {
        assertDoesNotThrow(() ->
                store.delete("actor1", "slack", "tenant1"));
    }

    @Test
    void lookupFindsStoredCredential() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "google-maps",
                "AIza-fake-key", CredentialType.API_KEY,
                Instant.now(), Instant.now()));

        var found = store.lookup("AIza-fake-key");
        assertThat(found).isPresent();
        assertThat(found.get().actorId()).isEqualTo("actor1");
    }

    @Test
    void lookupReturnsEmptyForUnknown() {
        assertThat(store.lookup("nonexistent")).isEmpty();
    }

    @Test
    void lookupDoesNotReturnRevokedCredentials() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "token-1", CredentialType.BOT_TOKEN,
                Instant.now(), Instant.now()));
        store.revoke("actor1", "slack", "tenant1");

        assertThat(store.lookup("token-1")).isEmpty();
    }

    @Test
    void lookupReturnsBothActiveAndGracePeriodCredentials() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "old-token", CredentialType.BOT_TOKEN,
                Instant.now(), Instant.now()));
        store.rotate("actor1", "slack", "tenant1", "new-token");

        assertThat(store.lookup("old-token")).isPresent();
        assertThat(store.lookup("new-token")).isPresent();
    }

    @Test
    void rotateCreatesNewActiveAndMovesOldToGracePeriod() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "google-maps",
                "old-key", CredentialType.API_KEY,
                Instant.now(), Instant.now()));

        var newRecord = store.rotate("actor1", "google-maps", "tenant1", "new-key");
        assertThat(newRecord.credential()).isEqualTo("new-key");
        assertThat(newRecord.state()).isEqualTo(CredentialState.ACTIVE);

        var active = store.find("actor1", "google-maps", "tenant1");
        assertThat(active).isPresent();
        assertThat(active.get().credential()).isEqualTo("new-key");
    }

    @Test
    void rotateWithExistingGracePeriodRevokesIt() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "key-v1", CredentialType.API_KEY,
                Instant.now(), Instant.now()));

        store.rotate("actor1", "slack", "tenant1", "key-v2");
        store.rotate("actor1", "slack", "tenant1", "key-v3");

        assertThat(store.lookup("key-v1")).isEmpty();
        assertThat(store.lookup("key-v2")).isPresent();
        assertThat(store.lookup("key-v3")).isPresent();
    }

    @Test
    void revokeMarksAllNonRevokedAsRevoked() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "key-v1", CredentialType.API_KEY,
                Instant.now(), Instant.now()));
        store.rotate("actor1", "slack", "tenant1", "key-v2");

        store.revoke("actor1", "slack", "tenant1");

        assertThat(store.lookup("key-v1")).isEmpty();
        assertThat(store.lookup("key-v2")).isEmpty();
        assertThat(store.find("actor1", "slack", "tenant1")).isEmpty();
    }

    @Test
    void revokeOnNonExistentIsNoOp() {
        assertThatCode(() -> store.revoke("actor1", "slack", "tenant1"))
                .doesNotThrowAnyException();
    }

    @Test
    void findReturnsOnlyActiveCredential() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "key-v1", CredentialType.API_KEY,
                Instant.now(), Instant.now()));
        store.rotate("actor1", "slack", "tenant1", "key-v2");

        var found = store.find("actor1", "slack", "tenant1");
        assertThat(found).isPresent();
        assertThat(found.get().credential()).isEqualTo("key-v2");
        assertThat(found.get().state()).isEqualTo(CredentialState.ACTIVE);
    }

    @Test
    void findAllReturnsOnlyActiveCredentials() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "google-maps",
                "key1", CredentialType.API_KEY, Instant.now(), Instant.now()));
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "github",
                "ghp_fake", CredentialType.PERSONAL_ACCESS_TOKEN,
                Instant.now(), Instant.now()));

        store.rotate("actor1", "google-maps", "tenant1", "key2");

        var results = store.findAll("actor1", "tenant1");
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(r -> r.state() == CredentialState.ACTIVE);
    }

    @Test
    void deleteRemovesAllStatesForTuple() {
        store.store(new StaticCredentialRecord(
                "actor1", "tenant1", "slack",
                "key-v1", CredentialType.API_KEY,
                Instant.now(), Instant.now()));
        store.rotate("actor1", "slack", "tenant1", "key-v2");

        store.delete("actor1", "slack", "tenant1");

        assertThat(store.find("actor1", "slack", "tenant1")).isEmpty();
        assertThat(store.lookup("key-v1")).isEmpty();
        assertThat(store.lookup("key-v2")).isEmpty();
    }
}
