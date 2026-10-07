package io.casehub.platform.authn.webauthn.spring.jpa;

import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringWebAuthnCredentialStore.class)
@EnableJpaRepositories(basePackageClasses = WebAuthnCredentialRepository.class)
public class WebAuthnJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(WebAuthnCredentialStore.class)
    public SpringWebAuthnCredentialStore springWebAuthnCredentialStore(WebAuthnCredentialRepository repo) {
        return new SpringWebAuthnCredentialStore(repo);
    }
}
