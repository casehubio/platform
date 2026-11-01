package io.casehub.platform.registration.core;

import io.casehub.platform.api.util.UUIDv7;
import io.casehub.platform.registration.EmailSender;
import io.casehub.platform.registration.EmailVerificationStore;
import io.casehub.platform.registration.EmailVerificationToken;
import io.casehub.platform.registration.InvitationCreated;
import io.casehub.platform.registration.InvitationCreationResult;
import io.casehub.platform.registration.InvitationRecord;
import io.casehub.platform.registration.InvitationRequest;
import io.casehub.platform.registration.InvitationStore;
import io.casehub.platform.registration.PasswordCredential;
import io.casehub.platform.registration.PasswordCredentialStore;
import io.casehub.platform.registration.PasswordHasher;
import io.casehub.platform.registration.PasswordRegistrationRequest;
import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationMode;
import io.casehub.platform.registration.RegistrationPolicy;
import io.casehub.platform.registration.RegistrationResult;
import io.casehub.platform.registration.RegistrationService;
import io.casehub.platform.registration.UserActivated;
import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserRegistered;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;
import io.casehub.platform.registration.RegistrationApproved;
import io.casehub.platform.registration.RegistrationRejected;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public class RegistrationServiceCore implements RegistrationService {

    private static final Duration VERIFICATION_TOKEN_TTL = Duration.ofHours(24);

    private final UserStore userStore;
    private final PasswordCredentialStore passwordCredentialStore;
    private final InvitationStore invitationStore;
    private final EmailVerificationStore emailVerificationStore;
    private final RegistrationPolicy registrationPolicy;
    private final EmailSender emailSender;
    private final RegistrationEventListener eventListener;
    private final PasswordHasher passwordHasher;

    public RegistrationServiceCore(UserStore userStore,
                                    PasswordCredentialStore passwordCredentialStore,
                                    InvitationStore invitationStore,
                                    EmailVerificationStore emailVerificationStore,
                                    RegistrationPolicy registrationPolicy,
                                    EmailSender emailSender,
                                    RegistrationEventListener eventListener,
                                    PasswordHasher passwordHasher) {
        this.userStore = Objects.requireNonNull(userStore);
        this.passwordCredentialStore = Objects.requireNonNull(passwordCredentialStore);
        this.invitationStore = Objects.requireNonNull(invitationStore);
        this.emailVerificationStore = Objects.requireNonNull(emailVerificationStore);
        this.registrationPolicy = Objects.requireNonNull(registrationPolicy);
        this.emailSender = Objects.requireNonNull(emailSender);
        this.eventListener = Objects.requireNonNull(eventListener);
        this.passwordHasher = Objects.requireNonNull(passwordHasher);
    }

    @Override
    public RegistrationResult registerWithPassword(PasswordRegistrationRequest request) {
        var mode = registrationPolicy.getMode(request.tenancyId());

        InvitationRecord invitation = null;
        if (request.invitationToken() != null) {
            String tokenHash = TokenGenerator.sha256Hex(request.invitationToken());
            invitation = invitationStore.findByTokenHash(tokenHash).orElse(null);
            if (invitation == null || invitation.consumed() || invitation.isExpired()) {
                throw new IllegalArgumentException("Invalid or expired invitation token");
            }
            if (!invitation.email().equalsIgnoreCase(request.email())) {
                throw new IllegalArgumentException("Invitation email does not match registration email");
            }
        } else if (mode == RegistrationMode.INVITATION_ONLY) {
            return new RegistrationResult.PolicyDenied(mode);
        }

        var existing = userStore.findByEmail(request.email(), request.tenancyId());
        if (existing.isPresent()) {
            if (existing.get().status() == UserStatus.REJECTED) {
                userStore.delete(existing.get().id(), request.tenancyId());
            } else {
                return new RegistrationResult.AlreadyExists(request.email());
            }
        }

        String userId = UUIDv7.generate().toString();
        var now = Instant.now();
        var user = userStore.create(new UserRecord(
                userId,
                request.email(),
                request.displayName() != null ? request.displayName() : request.email(),
                request.tenancyId(),
                UserStatus.PENDING_VERIFICATION,
                "password",
                now,
                null));

        String hashedPassword = passwordHasher.hash(request.password());
        passwordCredentialStore.store(new PasswordCredential(
                userId, request.tenancyId(), hashedPassword, now, null));

        String rawVerificationToken = TokenGenerator.generate();
        emailVerificationStore.store(new EmailVerificationToken(
                TokenGenerator.sha256Hex(rawVerificationToken),
                userId,
                request.tenancyId(),
                now.plus(VERIFICATION_TOKEN_TTL),
                now));

        emailSender.send(request.email(),
                "Verify your email",
                "Your verification token: " + rawVerificationToken);

        if (invitation != null) {
            invitationStore.consume(invitation.tokenHash());
        }

        eventListener.onUserRegistered(new UserRegistered(
                userId, request.tenancyId(), request.email(), "password"));

        return new RegistrationResult.PendingVerification(userId, request.email());
    }

    @Override
    public RegistrationResult verifyEmail(String rawToken) {
        var token = emailVerificationStore.consume(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification token"));

        var user = userStore.findById(token.userId(), token.tenancyId())
                .orElseThrow(() -> new IllegalStateException("User not found: " + token.userId()));

        boolean hasConsumedInvitation = invitationStore.findByEmail(user.email(), user.tenancyId())
                .map(InvitationRecord::consumed)
                .orElse(false);

        var mode = registrationPolicy.getMode(token.tenancyId());
        UserStatus newStatus;
        if (hasConsumedInvitation || mode == RegistrationMode.OPEN) {
            newStatus = UserStatus.ACTIVE;
        } else {
            newStatus = UserStatus.PENDING_APPROVAL;
        }

        var updated = userStore.updateStatus(user.id(), user.tenancyId(), newStatus);

        if (newStatus == UserStatus.ACTIVE) {
            eventListener.onUserActivated(new UserActivated(
                    updated.id(), updated.tenancyId(), updated.email()));
            return new RegistrationResult.Activated(updated);
        } else {
            return new RegistrationResult.PendingApproval(updated.id());
        }
    }

    @Override
    public RegistrationResult approveRegistration(String userId, String tenancyId) {
        var user = userStore.findById(userId, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.status() != UserStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Cannot approve user in status " + user.status() + "; expected PENDING_APPROVAL");
        }

        var updated = userStore.updateStatus(userId, tenancyId, UserStatus.ACTIVE);
        eventListener.onRegistrationApproved(new RegistrationApproved(
                userId, tenancyId, "admin"));
        return new RegistrationResult.Activated(updated);
    }

    @Override
    public RegistrationResult rejectRegistration(String userId, String tenancyId, String reason) {
        var user = userStore.findById(userId, tenancyId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.status() != UserStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Cannot reject user in status " + user.status() + "; expected PENDING_APPROVAL");
        }

        userStore.updateStatus(userId, tenancyId, UserStatus.REJECTED);
        eventListener.onRegistrationRejected(new RegistrationRejected(
                userId, tenancyId, reason, "admin"));
        return new RegistrationResult.Rejected(userId, reason);
    }

    @Override
    public InvitationCreationResult createInvitation(InvitationRequest request) {
        String rawToken = TokenGenerator.generate();
        String tokenHash = TokenGenerator.sha256Hex(rawToken);
        String id = UUIDv7.generate().toString();
        var now = Instant.now();

        var record = invitationStore.store(new InvitationRecord(
                id,
                tokenHash,
                request.email(),
                request.tenancyId(),
                request.assignedGroups(),
                request.expiresAt() != null ? request.expiresAt() : now.plus(Duration.ofDays(7)),
                "admin",
                now,
                false));

        eventListener.onInvitationCreated(new InvitationCreated(
                tokenHash, request.email(), request.tenancyId(), "admin"));

        return new InvitationCreationResult(rawToken, record);
    }

    @Override
    public void revokeInvitation(String id) {
        invitationStore.delete(id);
    }
}
