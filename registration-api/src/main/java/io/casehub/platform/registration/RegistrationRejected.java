package io.casehub.platform.registration;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record RegistrationRejected(
    String userId,
    String tenancyId,
    String reason,
    String rejectedBy
) implements SubscribableEvent {

    @Override
    public String type() {
        return "io.casehub.registration.rejected";
    }
}
