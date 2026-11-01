package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.EmailVerificationStore;
import io.casehub.platform.registration.InvitationStore;
import io.casehub.platform.registration.PasswordCredentialStore;
import io.casehub.platform.registration.UserStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringUserStore.class)
@EnableJpaRepositories(basePackageClasses = UserEntityRepository.class)
public class UserJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(UserStore.class)
    public SpringUserStore springUserStore(UserEntityRepository repo) {
        return new SpringUserStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordCredentialStore.class)
    public SpringPasswordCredentialStore springPasswordCredentialStore(PasswordCredentialEntityRepository repo) {
        return new SpringPasswordCredentialStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(InvitationStore.class)
    public SpringInvitationStore springInvitationStore(InvitationEntityRepository repo) {
        return new SpringInvitationStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(EmailVerificationStore.class)
    public SpringEmailVerificationStore springEmailVerificationStore(EmailVerificationEntityRepository repo) {
        return new SpringEmailVerificationStore(repo);
    }
}
