package io.casehub.platform.registration;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record RegistrationApproved(
    String userId,
    String tenancyId,
    String approvedBy
) implements SubscribableEvent {

    @Override
    public String type() {
        return "io.casehub.registration.approved";
    }
}
