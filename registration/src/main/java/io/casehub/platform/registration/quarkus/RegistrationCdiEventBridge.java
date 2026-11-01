package io.casehub.platform.registration.quarkus;

import io.casehub.platform.registration.InvitationCreated;
import io.casehub.platform.registration.PasswordResetCompleted;
import io.casehub.platform.registration.PasswordResetRequested;
import io.casehub.platform.registration.RegistrationApproved;
import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationRejected;
import io.casehub.platform.registration.UserActivated;
import io.casehub.platform.registration.UserRegistered;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

@ApplicationScoped
public class RegistrationCdiEventBridge implements RegistrationEventListener {

    @Inject
    Event<UserRegistered> userRegisteredEvent;

    @Inject
    Event<UserActivated> userActivatedEvent;

    @Inject
    Event<RegistrationApproved> registrationApprovedEvent;

    @Inject
    Event<RegistrationRejected> registrationRejectedEvent;

    @Inject
    Event<InvitationCreated> invitationCreatedEvent;

    @Override
    public void onUserRegistered(UserRegistered event) {
        userRegisteredEvent.fire(event);
    }

    @Override
    public void onUserActivated(UserActivated event) {
        userActivatedEvent.fire(event);
    }

    @Override
    public void onRegistrationApproved(RegistrationApproved event) {
        registrationApprovedEvent.fire(event);
    }

    @Override
    public void onRegistrationRejected(RegistrationRejected event) {
        registrationRejectedEvent.fire(event);
    }

    @Override
    public void onInvitationCreated(InvitationCreated event) {
        invitationCreatedEvent.fire(event);
    }

    @Inject
    Event<PasswordResetRequested> passwordResetRequestedEvent;

    @Inject
    Event<PasswordResetCompleted> passwordResetCompletedEvent;

    @Override
    public void onPasswordResetRequested(PasswordResetRequested event) {
        passwordResetRequestedEvent.fire(event);
    }

    @Override
    public void onPasswordResetCompleted(PasswordResetCompleted event) {
        passwordResetCompletedEvent.fire(event);
    }

}
