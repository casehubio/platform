package io.casehub.platform.registration.core;

import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationMode;
import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.inmem.InMemoryUserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserStoreProvisionerTest {

    private InMemoryUserStore userStore;
    private RegistrationMode policyMode;
    private UserStoreProvisioner provisioner;

    @BeforeEach
    void setUp() {
        userStore = new InMemoryUserStore();
        policyMode = RegistrationMode.OPEN;
        provisioner = new UserStoreProvisioner(
                userStore, tenancyId -> policyMode, new RegistrationEventListener() {});
    }

    @Test
    void provision_openPolicy_verifiedEmail_createsActiveUser() {
        var result = provisioner.provisionFromSocialLogin("alice@example.com", "t1", "google", true);

        assertThat(result).isPresent();
        var user = userStore.findByEmail("alice@example.com", "t1").orElseThrow();
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.registrationMethod()).isEqualTo("google");
        assertThat(user.activatedAt()).isNotNull();
    }

    @Test
    void provision_approvalRequired_createsPendingApprovalUser() {
        policyMode = RegistrationMode.APPROVAL_REQUIRED;
        provisioner = new UserStoreProvisioner(
                userStore, tenancyId -> policyMode, new RegistrationEventListener() {});

        var result = provisioner.provisionFromSocialLogin("bob@example.com", "t1", "github", true);

        assertThat(result).isPresent();
        var user = userStore.findByEmail("bob@example.com", "t1").orElseThrow();
        assertThat(user.status()).isEqualTo(UserStatus.PENDING_APPROVAL);
        assertThat(user.activatedAt()).isNull();
    }

    @Test
    void provision_invitationOnly_returnsEmpty() {
        policyMode = RegistrationMode.INVITATION_ONLY;
        provisioner = new UserStoreProvisioner(
                userStore, tenancyId -> policyMode, new RegistrationEventListener() {});

        var result = provisioner.provisionFromSocialLogin("carol@example.com", "t1", "google", true);

        assertThat(result).isEmpty();
        assertThat(userStore.findByEmail("carol@example.com", "t1")).isEmpty();
    }

    @Test
    void provision_existingActiveUser_returnsEmpty() {
        userStore.create(new UserRecord("existing", "dave@example.com", "Dave", "t1",
                UserStatus.ACTIVE, "password", Instant.now(), Instant.now()));

        var result = provisioner.provisionFromSocialLogin("dave@example.com", "t1", "google", true);

        assertThat(result).isEmpty();
    }

    @Test
    void provision_rejectedUser_deletesAndReCreates() {
        userStore.create(new UserRecord("old", "eve@example.com", "Eve", "t1",
                UserStatus.REJECTED, "password", Instant.now(), null));

        var result = provisioner.provisionFromSocialLogin("eve@example.com", "t1", "google", true);

        assertThat(result).isPresent();
        var user = userStore.findByEmail("eve@example.com", "t1").orElseThrow();
        assertThat(user.id()).isNotEqualTo("old");
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void provision_unverifiedEmail_returnsEmpty() {
        var result = provisioner.provisionFromSocialLogin("frank@example.com", "t1", "github", false);

        assertThat(result).isEmpty();
        assertThat(userStore.findByEmail("frank@example.com", "t1")).isEmpty();
    }
}
