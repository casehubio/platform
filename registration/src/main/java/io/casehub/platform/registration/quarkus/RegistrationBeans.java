package io.casehub.platform.registration.quarkus;

import io.casehub.platform.api.authn.UserResolver;
import io.casehub.platform.api.authn.UserStatusResolver;
import io.casehub.platform.registration.EmailSender;
import io.casehub.platform.registration.EmailVerificationStore;
import io.casehub.platform.registration.InvitationStore;
import io.casehub.platform.registration.PasswordCredentialStore;
import io.casehub.platform.registration.PasswordHasher;
import io.casehub.platform.registration.RegistrationEventListener;
import io.casehub.platform.registration.RegistrationPolicy;
import io.casehub.platform.registration.RegistrationService;
import io.casehub.platform.registration.UserProvisioner;
import io.casehub.platform.registration.UserStore;
import io.casehub.platform.registration.core.Argon2PasswordHasher;
import io.casehub.platform.registration.core.PasswordAuthenticationProviderCore;
import io.casehub.platform.registration.core.RegistrationServiceCore;
import io.casehub.platform.registration.core.UserStoreProvisioner;
import io.casehub.platform.registration.core.UserStoreUserResolver;
import io.casehub.platform.registration.core.SocialLoginReconciliationHandler;
import io.casehub.platform.registration.core.UserStoreUserStatusResolver;
import io.quarkus.arc.DefaultBean;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;

import java.util.logging.Logger;

@ApplicationScoped
public class RegistrationBeans {

    private static final Logger LOG = Logger.getLogger(RegistrationBeans.class.getName());

    @Produces
    @ApplicationScoped
    public RegistrationService registrationService(UserStore userStore,
                                                    PasswordCredentialStore passwordStore,
                                                    InvitationStore invitationStore,
                                                    EmailVerificationStore verificationStore,
                                                    RegistrationPolicy policy,
                                                    EmailSender emailSender,
                                                    RegistrationEventListener eventListener,
                                                    PasswordHasher passwordHasher) {
        return new RegistrationServiceCore(
                userStore, passwordStore, invitationStore, verificationStore,
                policy, emailSender, eventListener, passwordHasher);
    }

    @Produces
    @Alternative
    @Priority(200)
    @ApplicationScoped
    public UserProvisioner userStoreProvisioner(UserStore userStore,
                                                RegistrationPolicy policy,
                                                RegistrationEventListener eventListener) {
        return new UserStoreProvisioner(userStore, policy, eventListener);
    }

    @Produces
    @Alternative
    @Priority(200)
    @ApplicationScoped
    public UserResolver userStoreUserResolver(UserStore userStore) {
        return new UserStoreUserResolver(userStore);
    }

    @Produces
    @Alternative
    @Priority(200)
    @ApplicationScoped
    public UserStatusResolver userStoreUserStatusResolver(UserStore userStore) {
        return new UserStoreUserStatusResolver(userStore);
    }

    @Produces
    @ApplicationScoped
    public PasswordHasher argon2PasswordHasher() {
        return new Argon2PasswordHasher();
    }

    @Produces
    @ApplicationScoped
    public io.casehub.platform.api.authn.AuthenticationProvider passwordAuthenticationProvider(
            PasswordCredentialStore credentialStore,
            UserStore userStore,
            PasswordHasher passwordHasher) {
        return new PasswordAuthenticationProviderCore(credentialStore, userStore, passwordHasher);
    }

    @Produces
    @ApplicationScoped
    public SocialLoginReconciliationHandler socialLoginReconciliationHandler(
            UserStore userStore,
            RegistrationPolicy policy,
            RegistrationEventListener eventListener) {
        return new SocialLoginReconciliationHandler(userStore, policy, eventListener);
    }

    @Produces
    @DefaultBean
    @ApplicationScoped
    public EmailSender noOpEmailSender() {
        return (to, subject, body) ->
                LOG.warning("No EmailSender configured — email to " + to + " not sent: " + subject);
    }
}
