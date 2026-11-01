package io.casehub.platform.registration;

import io.casehub.platform.api.subscription.SubscribableEvent;

public record InvitationCreated(
    String tokenHash,
    String email,
    String tenancyId,
    String createdBy
) implements SubscribableEvent {

    @Override
    public String type() {
        return "io.casehub.registration.invitation.created";
    }
}
