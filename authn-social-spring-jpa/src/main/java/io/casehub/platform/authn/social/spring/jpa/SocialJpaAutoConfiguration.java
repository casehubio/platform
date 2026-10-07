package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.api.authn.IdentityBindingStore;
import io.casehub.platform.api.authn.OAuthTokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringIdentityBindingStore.class)
@EnableJpaRepositories(basePackageClasses = IdentityBindingRepository.class)
public class SocialJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdentityBindingStore.class)
    public SpringIdentityBindingStore springIdentityBindingStore(IdentityBindingRepository repo) {
        return new SpringIdentityBindingStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(OAuthTokenStore.class)
    public SpringOAuthTokenStore springOAuthTokenStore(OAuthTokenRepository repo) {
        return new SpringOAuthTokenStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(io.casehub.platform.api.authn.StaticCredentialStore.class)
    public SpringStaticCredentialStore springStaticCredentialStore(StaticCredentialRepository repo) {
        return new SpringStaticCredentialStore(repo);
    }

}
