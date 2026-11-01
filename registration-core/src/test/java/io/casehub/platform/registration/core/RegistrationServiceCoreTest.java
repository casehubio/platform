package io.casehub.platform.registration.core;

import io.casehub.platform.registration.EmailSender;
import io.casehub.platform.registration.InvitationCreated;
import io.casehub.platform.registration.InvitationRequest;
import io.casehub.platform.registration.PasswordPolicy;
import io.casehub.platform.registration.PasswordPolicyResult;
import io.casehub.platform.registration.PasswordPolicyViolationException;
import io.casehub.platform.registration.PasswordRegistrationRequest;
import io.casehub.platform.registration.RegistrationApproved;
import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationMode;
import io.casehub.platform.registration.RegistrationRejected;
import io.casehub.platform.registration.RegistrationResult;
import io.casehub.platform.registration.UserActivated;
import io.casehub.platform.registration.UserRegistered;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.inmem.InMemoryEmailVerificationStore;
import io.casehub.platform.registration.inmem.InMemoryInvitationStore;
import io.casehub.platform.registration.inmem.InMemoryPasswordCredentialStore;
import io.casehub.platform.registration.inmem.InMemoryPasswordResetStore;
import io.casehub.platform.registration.inmem.InMemoryUserStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistrationServiceCoreTest {

    private InMemoryUserStore userStore;
    private InMemoryPasswordCredentialStore passwordStore;
    private InMemoryInvitationStore invitationStore;
    private InMemoryEmailVerificationStore verificationStore;
    private RecordingEmailSender emailSender;
    private RecordingEventListener eventListener;
    private TestPasswordHasher passwordHasher;
    private InMemoryPasswordResetStore resetStore;
    private RegistrationMode policyMode;
    private RegistrationServiceCore service;

    @BeforeEach
    void setUp() {
        userStore = new InMemoryUserStore();
        passwordStore = new InMemoryPasswordCredentialStore();
        invitationStore = new InMemoryInvitationStore();
        verificationStore = new InMemoryEmailVerificationStore();
        emailSender = new RecordingEmailSender();
        eventListener = new RecordingEventListener();
        passwordHasher = new TestPasswordHasher();
        resetStore = new InMemoryPasswordResetStore();
        policyMode = RegistrationMode.OPEN;
        rebuildService();
    }

    private void rebuildService() {
        service = new RegistrationServiceCore(
                userStore, passwordStore, invitationStore, verificationStore,
                tenancyId -> policyMode, emailSender, eventListener, passwordHasher,
                password -> PasswordPolicyResult.ok(), resetStore);
    }

    private void rebuildServiceWithPolicy(PasswordPolicy policy) {
        service = new RegistrationServiceCore(
                userStore, passwordStore, invitationStore, verificationStore,
                tenancyId -> policyMode, emailSender, eventListener, passwordHasher,
                policy, resetStore);
    }

    @Test
    void registerWithPassword_openPolicy_createsPendingVerification() {
        var result = service.registerWithPassword(
                new PasswordRegistrationRequest("alice@example.com", "secret", "Alice", "t1", null));

        assertThat(result).isInstanceOf(RegistrationResult.PendingVerification.class);
        var pv = (RegistrationResult.PendingVerification) result;
        assertThat(pv.email()).isEqualTo("alice@example.com");

        var user = userStore.findByEmail("alice@example.com", "t1").orElseThrow();
        assertThat(user.status()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        assertThat(passwordStore.findByActorId(user.id(), "t1")).isPresent();
        assertThat(emailSender.sent).hasSize(1);
        assertThat(eventListener.registeredEvents).hasSize(1);
    }

    @Test
    void registerWithPassword_invitationOnly_withoutToken_returnsPolicyDenied() {
        policyMode = RegistrationMode.INVITATION_ONLY;
        rebuildService();

        var result = service.registerWithPassword(
                new PasswordRegistrationRequest("bob@example.com", "secret", null, "t1", null));

        assertThat(result).isInstanceOf(RegistrationResult.PolicyDenied.class);
        assertThat(((RegistrationResult.PolicyDenied) result).mode()).isEqualTo(RegistrationMode.INVITATION_ONLY);
    }

    @Test
    void registerWithPassword_invitationOnly_withValidToken_succeeds() {
        policyMode = RegistrationMode.INVITATION_ONLY;
        rebuildService();

        var invitation = service.createInvitation(
                new InvitationRequest("carol@example.com", "t1", Set.of("users"), null));

        var result = service.registerWithPassword(
                new PasswordRegistrationRequest("carol@example.com", "secret", "Carol", "t1", invitation.rawToken()));

        assertThat(result).isInstanceOf(RegistrationResult.PendingVerification.class);
    }

    @Test
    void registerWithPassword_duplicateEmail_returnsAlreadyExists() {
        service.registerWithPassword(
                new PasswordRegistrationRequest("dup@example.com", "secret", null, "t1", null));

        var result = service.registerWithPassword(
                new PasswordRegistrationRequest("dup@example.com", "other", null, "t1", null));

        assertThat(result).isInstanceOf(RegistrationResult.AlreadyExists.class);
    }

    @Test
    void registerWithPassword_rejectedUser_deletesAndReRegisters() {
        service.registerWithPassword(
                new PasswordRegistrationRequest("rejected@example.com", "secret", null, "t1", null));
        var user = userStore.findByEmail("rejected@example.com", "t1").orElseThrow();
        userStore.updateStatus(user.id(), "t1", UserStatus.PENDING_APPROVAL);
        userStore.updateStatus(user.id(), "t1", UserStatus.REJECTED);

        var result = service.registerWithPassword(
                new PasswordRegistrationRequest("rejected@example.com", "newpass", null, "t1", null));

        assertThat(result).isInstanceOf(RegistrationResult.PendingVerification.class);
    }

    @Test
    void verifyEmail_openPolicy_activatesUser() {
        service.registerWithPassword(
                new PasswordRegistrationRequest("verify@example.com", "secret", null, "t1", null));

        String rawToken = extractVerificationToken();
        var result = service.verifyEmail(rawToken);

        assertThat(result).isInstanceOf(RegistrationResult.Activated.class);
        var activated = (RegistrationResult.Activated) result;
        assertThat(activated.user().status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(activated.user().activatedAt()).isNotNull();
    }

    @Test
    void verifyEmail_approvalRequired_movesToPendingApproval() {
        policyMode = RegistrationMode.APPROVAL_REQUIRED;
        rebuildService();

        service.registerWithPassword(
                new PasswordRegistrationRequest("approve@example.com", "secret", null, "t1", null));

        String rawToken = extractVerificationToken();
        var result = service.verifyEmail(rawToken);

        assertThat(result).isInstanceOf(RegistrationResult.PendingApproval.class);
    }

    @Test
    void verifyEmail_withConsumedInvitation_bypassesApproval() {
        policyMode = RegistrationMode.APPROVAL_REQUIRED;
        rebuildService();

        var invitation = service.createInvitation(
                new InvitationRequest("invited@example.com", "t1", Set.of(), null));
        service.registerWithPassword(
                new PasswordRegistrationRequest("invited@example.com", "secret", null, "t1", invitation.rawToken()));

        String rawToken = extractVerificationToken();
        var result = service.verifyEmail(rawToken);

        assertThat(result).isInstanceOf(RegistrationResult.Activated.class);
    }

    @Test
    void verifyEmail_invalidToken_throws() {
        assertThatThrownBy(() -> service.verifyEmail("bogus-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approveRegistration_pendingApproval_activates() {
        policyMode = RegistrationMode.APPROVAL_REQUIRED;
        rebuildService();

        service.registerWithPassword(
                new PasswordRegistrationRequest("toapprove@example.com", "secret", null, "t1", null));
        String rawToken = extractVerificationToken();
        service.verifyEmail(rawToken);

        var user = userStore.findByEmail("toapprove@example.com", "t1").orElseThrow();
        var result = service.approveRegistration(user.id(), "t1");

        assertThat(result).isInstanceOf(RegistrationResult.Activated.class);
        assertThat(eventListener.approvedEvents).hasSize(1);
    }

    @Test
    void approveRegistration_notPending_throws() {
        service.registerWithPassword(
                new PasswordRegistrationRequest("notpending@example.com", "secret", null, "t1", null));
        var user = userStore.findByEmail("notpending@example.com", "t1").orElseThrow();

        assertThatThrownBy(() -> service.approveRegistration(user.id(), "t1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectRegistration_pendingApproval_rejects() {
        policyMode = RegistrationMode.APPROVAL_REQUIRED;
        rebuildService();

        service.registerWithPassword(
                new PasswordRegistrationRequest("toreject@example.com", "secret", null, "t1", null));
        String rawToken = extractVerificationToken();
        service.verifyEmail(rawToken);

        var user = userStore.findByEmail("toreject@example.com", "t1").orElseThrow();
        var result = service.rejectRegistration(user.id(), "t1", "Not eligible");

        assertThat(result).isInstanceOf(RegistrationResult.Rejected.class);
        assertThat(eventListener.rejectedEvents).hasSize(1);
    }

    @Test
    void createInvitation_generatesTokenAndRecord() {
        var result = service.createInvitation(
                new InvitationRequest("inv@example.com", "t1", Set.of("admin"), null));

        assertThat(result.rawToken()).isNotNull().isNotEmpty();
        assertThat(result.record().email()).isEqualTo("inv@example.com");
        assertThat(result.record().assignedGroups()).containsExactly("admin");
        assertThat(result.record().consumed()).isFalse();
        assertThat(eventListener.invitationEvents).hasSize(1);
    }

    @Test
    void revokeInvitation_deletesRecord() {
        var result = service.createInvitation(
                new InvitationRequest("revoke@example.com", "t1", Set.of(), null));

        service.revokeInvitation(result.record().id());

        assertThat(invitationStore.findById(result.record().id())).isEmpty();
    }

    @Test
    void registerWithPassword_weakPassword_throwsPolicyViolation() {
        rebuildServiceWithPolicy(new DefaultPasswordPolicy(null));
        assertThatThrownBy(() -> service.registerWithPassword(
                new PasswordRegistrationRequest("user@example.com", "weak", null, "t1", null)))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .satisfies(ex -> assertThat(((PasswordPolicyViolationException) ex).violations()).isNotEmpty());
    }

    @Test
    void registerWithPassword_strongPassword_succeeds() {
        rebuildServiceWithPolicy(new DefaultPasswordPolicy(null));
        var result = service.registerWithPassword(
                new PasswordRegistrationRequest("user@example.com", "Str0ngPass!", null, "t1", null));
        assertThat(result).isInstanceOf(RegistrationResult.PendingVerification.class);
    }

    @Test
    void requestPasswordReset_activeUser_sendsEmail() {
        registerAndActivateUser("user@example.com", "Str0ngPass!");
        int emailCountBefore = emailSender.sent.size();
        service.requestPasswordReset("user@example.com", "t1");
        assertThat(emailSender.sent).hasSize(emailCountBefore + 1);
        assertThat(emailSender.sent.get(emailSender.sent.size() - 1).subject()).contains("reset");
    }

    @Test
    void requestPasswordReset_unknownEmail_silentlySucceeds() {
        int emailCountBefore = emailSender.sent.size();
        service.requestPasswordReset("unknown@example.com", "t1");
        assertThat(emailSender.sent).hasSize(emailCountBefore);
    }

    @Test
    void requestPasswordReset_inactiveUser_silentlySucceeds() {
        service.registerWithPassword(
                new PasswordRegistrationRequest("user@example.com", "secret", null, "t1", null));
        int emailCountBefore = emailSender.sent.size();
        service.requestPasswordReset("user@example.com", "t1");
        assertThat(emailSender.sent).hasSize(emailCountBefore);
    }

    @Test
    void resetPassword_validToken_updatesPassword() {
        registerAndActivateUser("user@example.com", "Str0ngPass!");
        service.requestPasswordReset("user@example.com", "t1");
        String rawToken = extractResetToken();

        var result = service.resetPassword(rawToken, "NewStr0ng!", "t1");
        assertThat(result).isInstanceOf(RegistrationResult.Activated.class);
    }

    @Test
    void resetPassword_invalidToken_throws() {
        assertThatThrownBy(() -> service.resetPassword("bogus", "NewStr0ng!", "t1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPassword_weakNewPassword_throws() {
        rebuildServiceWithPolicy(new DefaultPasswordPolicy(null));
        registerAndActivateUser("user@example.com", "Str0ngPass!");
        service.requestPasswordReset("user@example.com", "t1");
        String rawToken = extractResetToken();

        assertThatThrownBy(() -> service.resetPassword(rawToken, "weak", "t1"))
                .isInstanceOf(PasswordPolicyViolationException.class);
    }

    @Test
    void resetPassword_tenancyMismatch_throws() {
        registerAndActivateUser("user@example.com", "Str0ngPass!");
        service.requestPasswordReset("user@example.com", "t1");
        String rawToken = extractResetToken();

        assertThatThrownBy(() -> service.resetPassword(rawToken, "NewStr0ng!", "wrong-tenant"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void registerAndActivateUser(String email, String password) {
        service.registerWithPassword(
                new PasswordRegistrationRequest(email, password, null, "t1", null));
        String verificationToken = extractVerificationToken();
        service.verifyEmail(verificationToken);
        var user = userStore.findByEmail(email, "t1").orElseThrow();
        if (user.status() == UserStatus.PENDING_APPROVAL) {
            service.approveRegistration(user.id(), "t1");
        }
    }

    private String extractResetToken() {
        var lastEmail = emailSender.sent.get(emailSender.sent.size() - 1);
        return lastEmail.body().replace("Your password reset token: ", "");
    }


    private String extractVerificationToken() {
        assertThat(emailSender.sent).isNotEmpty();
        var lastEmail = emailSender.sent.get(emailSender.sent.size() - 1);
        String body = lastEmail.body;
        return body.replace("Your verification token: ", "");
    }

    static class TestPasswordHasher implements io.casehub.platform.registration.PasswordHasher {
        @Override
        public String hash(String plaintext) {
            return "hashed:" + plaintext;
        }

        @Override
        public boolean verify(String plaintext, String hashed) {
            return hashed.equals("hashed:" + plaintext);
        }
    }

    static class RecordingEmailSender implements EmailSender {
        final List<Email> sent = new ArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            sent.add(new Email(to, subject, body));
        }

        record Email(String to, String subject, String body) {}
    }

    static class RecordingEventListener implements RegistrationEventListener {
        final List<UserRegistered> registeredEvents = new ArrayList<>();
        final List<UserActivated> activatedEvents = new ArrayList<>();
        final List<RegistrationApproved> approvedEvents = new ArrayList<>();
        final List<RegistrationRejected> rejectedEvents = new ArrayList<>();
        final List<InvitationCreated> invitationEvents = new ArrayList<>();

        @Override public void onUserRegistered(UserRegistered event) { registeredEvents.add(event); }
        @Override public void onUserActivated(UserActivated event) { activatedEvents.add(event); }
        @Override public void onRegistrationApproved(RegistrationApproved event) { approvedEvents.add(event); }
        @Override public void onRegistrationRejected(RegistrationRejected event) { rejectedEvents.add(event); }
        @Override public void onInvitationCreated(InvitationCreated event) { invitationEvents.add(event); }
    }
}
