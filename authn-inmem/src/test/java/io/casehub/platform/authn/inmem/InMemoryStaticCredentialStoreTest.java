package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
}
