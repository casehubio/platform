package io.casehub.platform.registration.core;

import io.casehub.platform.api.identity.PrincipalId;
import io.casehub.platform.api.util.UUIDv7;
import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationMode;
import io.casehub.platform.registration.RegistrationPolicy;
import io.casehub.platform.registration.UserActivated;
import io.casehub.platform.registration.UserProvisioner;
import io.casehub.platform.registration.UserRecord;
import io.casehub.platform.registration.UserRegistered;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class UserStoreProvisioner implements UserProvisioner {

    private final UserStore userStore;
    private final RegistrationPolicy registrationPolicy;
    private final RegistrationEventListener eventListener;

    public UserStoreProvisioner(UserStore userStore,
                                 RegistrationPolicy registrationPolicy,
                                 RegistrationEventListener eventListener) {
        this.userStore = Objects.requireNonNull(userStore);
        this.registrationPolicy = Objects.requireNonNull(registrationPolicy);
        this.eventListener = Objects.requireNonNull(eventListener);
    }

    @Override
    public Optional<PrincipalId> provisionFromSocialLogin(
            String email, String tenancyId, String provider, boolean emailVerified) {
        var mode = registrationPolicy.getMode(tenancyId);
        if (mode == RegistrationMode.INVITATION_ONLY) {
            return Optional.empty();
        }
        if (!emailVerified) {
            return Optional.empty();
        }

        var existing = userStore.findByEmail(email, tenancyId);
        if (existing.isPresent()) {
            if (existing.get().status() == UserStatus.REJECTED) {
                userStore.delete(existing.get().id(), tenancyId);
            } else {
                return Optional.empty();
            }
        }

        var now = Instant.now();
        var status = (mode == RegistrationMode.OPEN) ? UserStatus.ACTIVE : UserStatus.PENDING_APPROVAL;
        var user = userStore.create(new UserRecord(
                UUIDv7.generate().toString(),
                email,
                email,
                tenancyId,
                status,
                provider,
                now,
                status == UserStatus.ACTIVE ? now : null));

        eventListener.onUserRegistered(new UserRegistered(
                user.id(), tenancyId, email, provider));
        if (status == UserStatus.ACTIVE) {
            eventListener.onUserActivated(new UserActivated(
                    user.id(), tenancyId, email));
        }

        return Optional.of(PrincipalId.human(user.id()));
    }
}
