package io.casehub.platform.registration.inmem.quarkus;

import io.casehub.platform.registration.EmailVerificationStore;
import io.casehub.platform.registration.InvitationStore;
import io.casehub.platform.registration.PasswordCredentialStore;
import io.casehub.platform.registration.UserStore;
import io.casehub.platform.registration.inmem.InMemoryEmailVerificationStore;
import io.casehub.platform.registration.inmem.InMemoryInvitationStore;
import io.casehub.platform.registration.inmem.InMemoryPasswordCredentialStore;
import io.casehub.platform.registration.inmem.InMemoryUserStore;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class UserInmemBeans {

    @Produces
    @Alternative
    @Priority(100)
    @ApplicationScoped
    public UserStore inMemoryUserStore() {
        return new InMemoryUserStore();
    }

    @Produces
    @Alternative
    @Priority(100)
    @ApplicationScoped
    public PasswordCredentialStore inMemoryPasswordCredentialStore() {
        return new InMemoryPasswordCredentialStore();
    }

    @Produces
    @Alternative
    @Priority(100)
    @ApplicationScoped
    public InvitationStore inMemoryInvitationStore() {
        return new InMemoryInvitationStore();
    }

    @Produces
    @Alternative
    @Priority(100)
    @ApplicationScoped
    public EmailVerificationStore inMemoryEmailVerificationStore() {
        return new InMemoryEmailVerificationStore();
    }

    @Produces
    @Alternative
    @Priority(100)
    @ApplicationScoped
    public io.casehub.platform.registration.PasswordResetStore inMemoryPasswordResetStore() {
        return new io.casehub.platform.registration.inmem.InMemoryPasswordResetStore();
    }

}
