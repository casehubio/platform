package io.casehub.platform.registration.spring;

import io.casehub.platform.registration.InvitationCreated;
import io.casehub.platform.registration.RegistrationApproved;
import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationRejected;
import io.casehub.platform.registration.UserActivated;
import io.casehub.platform.registration.UserRegistered;
import org.springframework.context.ApplicationEventPublisher;

public class SpringRegistrationEventBridge implements RegistrationEventListener {

    private final ApplicationEventPublisher publisher;

    public SpringRegistrationEventBridge(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onUserRegistered(UserRegistered event) {
        publisher.publishEvent(event);
    }

    @Override
    public void onUserActivated(UserActivated event) {
        publisher.publishEvent(event);
    }

    @Override
    public void onRegistrationApproved(RegistrationApproved event) {
        publisher.publishEvent(event);
    }

    @Override
    public void onRegistrationRejected(RegistrationRejected event) {
        publisher.publishEvent(event);
    }

    @Override
    public void onInvitationCreated(InvitationCreated event) {
        publisher.publishEvent(event);
    }
}
