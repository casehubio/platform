package io.casehub.platform.registration.core;

import io.casehub.platform.api.authn.AccountNotActiveException;
import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.InvalidCredentialException;
import io.casehub.platform.registration.PasswordCredential;
import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.inmem.InMemoryPasswordCredentialStore;
import io.casehub.platform.registration.inmem.InMemoryUserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordAuthenticationProviderCoreTest {

    private InMemoryUserStore userStore;
    private InMemoryPasswordCredentialStore credentialStore;
    private PasswordAuthenticationProviderCore provider;

    @BeforeEach
    void setUp() {
        userStore = new InMemoryUserStore();
        credentialStore = new InMemoryPasswordCredentialStore();
        provider = new PasswordAuthenticationProviderCore(
                credentialStore, userStore,
                new RegistrationServiceCoreTest.TestPasswordHasher());
    }

    private ChallengeRecord challenge(String tenancyId) {
        return new ChallengeRecord("ch1", "password", tenancyId, null, Instant.now(), Instant.now().plusSeconds(300));
    }

    private void createActiveUserWithPassword(String id, String email, String tenancyId, String password) {
        userStore.create(new UserRecord(id, email, email, tenancyId,
                UserStatus.ACTIVE, "password", Instant.now(), Instant.now()));
        credentialStore.store(new PasswordCredential(id, tenancyId, "hashed:" + password, Instant.now(), null));
    }

    @Test
    void method_returnsPassword() {
        assertThat(provider.method()).isEqualTo("password");
    }

    @Test
    void verify_validCredentials_returnsResult() {
        createActiveUserWithPassword("u1", "alice@test.com", "t1", "secret");

        var result = provider.verify(challenge("t1"),
                Map.of("email", "alice@test.com", "password", "secret"));

        assertThat(result.principal().id()).isEqualTo("u1");
        assertThat(result.tenancyId()).isEqualTo("t1");
        assertThat(result.method()).isEqualTo("password");
        assertThat(result.metadata()).containsEntry("userStatus", "ACTIVE");
    }

    @Test
    void verify_invalidPassword_throwsInvalidCredential() {
        createActiveUserWithPassword("u2", "bob@test.com", "t1", "correct");

        assertThatThrownBy(() -> provider.verify(challenge("t1"),
                Map.of("email", "bob@test.com", "password", "wrong")))
                .isInstanceOf(InvalidCredentialException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void verify_unknownEmail_throwsInvalidCredential() {
        assertThatThrownBy(() -> provider.verify(challenge("t1"),
                Map.of("email", "nobody@test.com", "password", "any")))
                .isInstanceOf(InvalidCredentialException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void verify_pendingVerification_throwsAccountNotActive() {
        userStore.create(new UserRecord("u3", "pending@test.com", "pending", "t1",
                UserStatus.PENDING_VERIFICATION, "password", Instant.now(), null));

        assertThatThrownBy(() -> provider.verify(challenge("t1"),
                Map.of("email", "pending@test.com", "password", "any")))
                .isInstanceOf(AccountNotActiveException.class)
                .satisfies(ex -> assertThat(((AccountNotActiveException) ex).statusName())
                        .isEqualTo("PENDING_VERIFICATION"));
    }

    @Test
    void verify_suspended_throwsAccountNotActive() {
        userStore.create(new UserRecord("u4", "suspended@test.com", "suspended", "t1",
                UserStatus.SUSPENDED, "password", Instant.now(), Instant.now()));

        assertThatThrownBy(() -> provider.verify(challenge("t1"),
                Map.of("email", "suspended@test.com", "password", "any")))
                .isInstanceOf(AccountNotActiveException.class)
                .satisfies(ex -> assertThat(((AccountNotActiveException) ex).statusName())
                        .isEqualTo("SUSPENDED"));
    }

    @Test
    void verify_noPasswordCredential_throwsInvalidCredential() {
        userStore.create(new UserRecord("u5", "nocred@test.com", "nocred", "t1",
                UserStatus.ACTIVE, "google", Instant.now(), Instant.now()));

        assertThatThrownBy(() -> provider.verify(challenge("t1"),
                Map.of("email", "nocred@test.com", "password", "any")))
                .isInstanceOf(InvalidCredentialException.class);
    }
}
