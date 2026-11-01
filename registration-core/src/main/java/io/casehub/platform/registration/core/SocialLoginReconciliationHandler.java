package io.casehub.platform.registration.core;

import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationMode;
import io.casehub.platform.registration.RegistrationPolicy;
import io.casehub.platform.registration.UserActivated;
import io.casehub.platform.registration.UserStatus;
import io.casehub.platform.registration.UserStore;

import java.util.Objects;

public class SocialLoginReconciliationHandler {

    private final UserStore userStore;
    private final RegistrationPolicy registrationPolicy;
    private final RegistrationEventListener eventListener;

    public SocialLoginReconciliationHandler(UserStore userStore,
                                             RegistrationPolicy registrationPolicy,
                                             RegistrationEventListener eventListener) {
        this.userStore = Objects.requireNonNull(userStore);
        this.registrationPolicy = Objects.requireNonNull(registrationPolicy);
        this.eventListener = Objects.requireNonNull(eventListener);
    }

    public void onSocialLoginCompleted(String actorId, String tenancyId, boolean emailVerified) {
        if (!emailVerified) return;

        var user = userStore.findById(actorId, tenancyId).orElse(null);
        if (user == null || user.status() != UserStatus.PENDING_VERIFICATION) return;

        var mode = registrationPolicy.getMode(tenancyId);
        var newStatus = (mode == RegistrationMode.OPEN) ? UserStatus.ACTIVE : UserStatus.PENDING_APPROVAL;
        userStore.updateStatus(user.id(), tenancyId, newStatus);

        if (newStatus == UserStatus.ACTIVE) {
            eventListener.onUserActivated(new UserActivated(user.id(), tenancyId, user.email()));
        }
    }
}
