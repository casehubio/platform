package io.casehub.platform.credentials.spring;

import io.casehub.platform.api.credentials.CredentialResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration
public class CredentialsSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CredentialResolver.class)
    public EnvironmentCredentialResolver environmentCredentialResolver(Environment environment) {
        return new EnvironmentCredentialResolver(environment);
    }
}
