package io.casehub.platform.registration.core;

import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.inmem.InMemoryUserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserStoreUserResolverTest {

    private InMemoryUserStore userStore;
    private UserStoreUserResolver resolver;

    @BeforeEach
    void setUp() {
        userStore = new InMemoryUserStore();
        resolver = new UserStoreUserResolver(userStore);
    }

    @Test
    void resolve_activeUser_returnsPrincipalId() {
        userStore.create(new UserRecord("u1", "alice@example.com", "Alice", "t1",
                UserStatus.ACTIVE, "password", Instant.now(), Instant.now()));

        var result = resolver.resolveByEmail("alice@example.com", "t1");
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("u1");
    }

    @Test
    void resolve_pendingVerificationUser_returnsPrincipalId() {
        userStore.create(new UserRecord("u2", "bob@example.com", "Bob", "t1",
                UserStatus.PENDING_VERIFICATION, "password", Instant.now(), null));

        var result = resolver.resolveByEmail("bob@example.com", "t1");
        assertThat(result).isPresent();
    }

    @Test
    void resolve_pendingApprovalUser_returnsPrincipalId() {
        userStore.create(new UserRecord("u3", "carol@example.com", "Carol", "t1",
                UserStatus.PENDING_APPROVAL, "google", Instant.now(), null));

        var result = resolver.resolveByEmail("carol@example.com", "t1");
        assertThat(result).isPresent();
    }

    @Test
    void resolve_rejectedUser_returnsEmpty() {
        userStore.create(new UserRecord("u4", "dave@example.com", "Dave", "t1",
                UserStatus.REJECTED, "password", Instant.now(), null));

        var result = resolver.resolveByEmail("dave@example.com", "t1");
        assertThat(result).isEmpty();
    }

    @Test
    void resolve_suspendedUser_returnsEmpty() {
        userStore.create(new UserRecord("u5", "eve@example.com", "Eve", "t1",
                UserStatus.SUSPENDED, "password", Instant.now(), Instant.now()));

        var result = resolver.resolveByEmail("eve@example.com", "t1");
        assertThat(result).isEmpty();
    }

    @Test
    void resolve_noUser_returnsEmpty() {
        var result = resolver.resolveByEmail("nobody@example.com", "t1");
        assertThat(result).isEmpty();
    }
}
