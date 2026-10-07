package io.casehub.platform.authn.session.spring.jpa;

import io.casehub.platform.api.authn.RefreshTokenStore;
import io.casehub.platform.api.authn.SessionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringSessionStore.class)
@EnableJpaRepositories(basePackageClasses = SessionRepository.class)
public class SessionJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SessionStore.class)
    public SpringSessionStore springSessionStore(SessionRepository repo) {
        return new SpringSessionStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(RefreshTokenStore.class)
    public SpringRefreshTokenStore springRefreshTokenStore(RefreshTokenRepository repo) {
        return new SpringRefreshTokenStore(repo);
    }
}
