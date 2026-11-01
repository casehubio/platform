package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.InvitationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryInvitationStoreTest {

    private InMemoryInvitationStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryInvitationStore();
    }

    private InvitationRecord invitation(String id, String tokenHash, String email, String tenancyId) {
        return new InvitationRecord(id, tokenHash, email, tenancyId,
                Set.of("users"), Instant.now().plusSeconds(3600), "admin", Instant.now(), false);
    }

    @Test
    void storeAndFindByTokenHash() {
        store.store(invitation("i1", "hash1", "alice@example.com", "t1"));
        var found = store.findByTokenHash("hash1");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("i1");
    }

    @Test
    void storeAndFindById() {
        store.store(invitation("i1", "hash1", "alice@example.com", "t1"));
        var found = store.findById("i1");
        assertThat(found).isPresent();
        assertThat(found.get().tokenHash()).isEqualTo("hash1");
    }

    @Test
    void findByEmail() {
        store.store(invitation("i1", "hash1", "alice@example.com", "t1"));
        var found = store.findByEmail("alice@example.com", "t1");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("i1");
    }

    @Test
    void findByEmailIsCaseInsensitive() {
        store.store(invitation("i1", "hash1", "Alice@Example.COM", "t1"));
        assertThat(store.findByEmail("alice@example.com", "t1")).isPresent();
    }

    @Test
    void consumeMarksConsumed() {
        store.store(invitation("i1", "hash1", "alice@example.com", "t1"));
        store.consume("hash1");
        var found = store.findByTokenHash("hash1");
        assertThat(found).isPresent();
        assertThat(found.get().consumed()).isTrue();
    }

    @Test
    void findByTenancyReturnsAllForTenant() {
        store.store(invitation("i1", "h1", "a@example.com", "t1"));
        store.store(invitation("i2", "h2", "b@example.com", "t1"));
        store.store(invitation("i3", "h3", "c@example.com", "t2"));
        var results = store.findByTenancy("t1");
        assertThat(results).hasSize(2);
    }

    @Test
    void deleteRemoves() {
        store.store(invitation("i1", "hash1", "alice@example.com", "t1"));
        store.delete("i1");
        assertThat(store.findById("i1")).isEmpty();
    }
}
