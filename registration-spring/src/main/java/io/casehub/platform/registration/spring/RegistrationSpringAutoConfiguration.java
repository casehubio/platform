package io.casehub.platform.registration.spring;

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
import io.casehub.platform.registration.core.UserStoreUserStatusResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import java.util.logging.Logger;

@AutoConfiguration
public class RegistrationSpringAutoConfiguration {

    private static final Logger LOG = Logger.getLogger(RegistrationSpringAutoConfiguration.class.getName());

    @Bean
    @ConditionalOnMissingBean
    public PasswordHasher passwordHasher() {
        return new Argon2PasswordHasher();
    }

    @Bean
    @ConditionalOnMissingBean
    public EmailSender emailSender() {
        return (to, subject, body) ->
                LOG.warning("No EmailSender configured — email to " + to + " not sent: " + subject);
    }

    @Bean
    @ConditionalOnMissingBean
    public RegistrationEventListener registrationEventListener(ApplicationEventPublisher publisher) {
        return new SpringRegistrationEventBridge(publisher);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(UserStore.class)
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(UserStore.class)
    public UserProvisioner userProvisioner(UserStore userStore,
                                           RegistrationPolicy policy,
                                           RegistrationEventListener eventListener) {
        return new UserStoreProvisioner(userStore, policy, eventListener);
    }

    @Bean
    @ConditionalOnMissingBean(UserResolver.class)
    @ConditionalOnBean(UserStore.class)
    public UserResolver userResolver(UserStore userStore) {
        return new UserStoreUserResolver(userStore);
    }

    @Bean
    @ConditionalOnMissingBean(UserStatusResolver.class)
    @ConditionalOnBean(UserStore.class)
    public UserStatusResolver userStatusResolver(UserStore userStore) {
        return new UserStoreUserStatusResolver(userStore);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PasswordCredentialStore.class, UserStore.class})
    public PasswordAuthenticationProviderCore passwordAuthenticationProvider(
            PasswordCredentialStore credentialStore,
            UserStore userStore,
            PasswordHasher passwordHasher) {
        return new PasswordAuthenticationProviderCore(credentialStore, userStore, passwordHasher);
    }
}
