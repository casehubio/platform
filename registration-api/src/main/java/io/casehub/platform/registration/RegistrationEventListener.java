package io.casehub.platform.registration;

public interface RegistrationEventListener {
    default void onUserRegistered(UserRegistered event)              {}
    default void onUserActivated(UserActivated event)                {}
    default void onRegistrationApproved(RegistrationApproved event)  {}
    default void onRegistrationRejected(RegistrationRejected event)  {}
    default void onInvitationCreated(InvitationCreated event)        {}
}
