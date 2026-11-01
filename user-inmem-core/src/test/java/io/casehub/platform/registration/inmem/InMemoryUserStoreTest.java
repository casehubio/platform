package io.casehub.platform.registration.inmem;

import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryUserStoreTest {

    private InMemoryUserStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryUserStore();
    }

    private UserRecord user(String id, String email, String tenancyId, UserStatus status) {
        return new UserRecord(id, email, null, tenancyId, status, "password", Instant.now(), null);
    }

    @Test
    void createAndFindById() {
        var created = store.create(user("u1", "alice@example.com", "t1", UserStatus.ACTIVE));
        var found = store.findById("u1", "t1");
        assertThat(found).isPresent().contains(created);
    }

    @Test
    void createAndFindByEmail() {
        store.create(user("u1", "alice@example.com", "t1", UserStatus.ACTIVE));
        var found = store.findByEmail("alice@example.com", "t1");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo("u1");
    }

    @Test
    void findByEmailIsCaseInsensitive() {
        store.create(user("u1", "Alice@Example.COM", "t1", UserStatus.ACTIVE));
        assertThat(store.findByEmail("alice@example.com", "t1")).isPresent();
    }

    @Test
    void duplicateEmailInSameTenantThrows() {
        store.create(user("u1", "alice@example.com", "t1", UserStatus.ACTIVE));
        assertThatThrownBy(() -> store.create(user("u2", "alice@example.com", "t1", UserStatus.ACTIVE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameEmailInDifferentTenantIsAllowed() {
        store.create(user("u1", "alice@example.com", "t1", UserStatus.ACTIVE));
        store.create(user("u2", "alice@example.com", "t2", UserStatus.ACTIVE));
        assertThat(store.findByEmail("alice@example.com", "t1")).isPresent();
        assertThat(store.findByEmail("alice@example.com", "t2")).isPresent();
    }

    @Test
    void updateStatusToActiveSetsActivatedAt() {
        store.create(user("u1", "alice@example.com", "t1", UserStatus.PENDING_VERIFICATION));
        var updated = store.updateStatus("u1", "t1", UserStatus.ACTIVE);
        assertThat(updated.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(updated.activatedAt()).isNotNull();
    }

    @Test
    void updateStatusPreservesExistingActivatedAt() {
        var original = new UserRecord("u1", "alice@example.com", null, "t1",
                UserStatus.ACTIVE, "password", Instant.now(), Instant.parse("2026-01-01T00:00:00Z"));
        store.create(original);
        var updated = store.updateStatus("u1", "t1", UserStatus.SUSPENDED);
        assertThat(updated.activatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void deleteRemovesUser() {
        store.create(user("u1", "alice@example.com", "t1", UserStatus.ACTIVE));
        store.delete("u1", "t1");
        assertThat(store.findById("u1", "t1")).isEmpty();
    }

    @Test
    void findByEmailWithWrongTenancyReturnsEmpty() {
        store.create(user("u1", "alice@example.com", "t1", UserStatus.ACTIVE));
        assertThat(store.findByEmail("alice@example.com", "t2")).isEmpty();
    }

    @Test
    void findByIdWithWrongTenancyReturnsEmpty() {
        store.create(user("u1", "alice@example.com", "t1", UserStatus.ACTIVE));
        assertThat(store.findById("u1", "t2")).isEmpty();
    }
}
