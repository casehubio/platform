package io.casehub.platform.registration;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record UserRegistered(
    String userId,
    String tenancyId,
    String email,
    String registrationMethod
) implements SubscribableEvent {

    @Override
    public String type() {
        return "io.casehub.registration.user.registered";
    }
}
