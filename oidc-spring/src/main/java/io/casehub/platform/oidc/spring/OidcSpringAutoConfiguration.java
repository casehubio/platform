package io.casehub.platform.oidc.spring;

import io.casehub.platform.api.identity.CurrentPrincipal;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.RequestScope;

@AutoConfiguration
public class OidcSpringAutoConfiguration {

    @Bean
    @RequestScope
    @ConditionalOnMissingBean(CurrentPrincipal.class)
    public SpringSecurityCurrentPrincipal springSecurityCurrentPrincipal() {
        return new SpringSecurityCurrentPrincipal();
    }

    @Bean
    @ConditionalOnMissingBean(MissingTenancyExceptionHandler.class)
    public MissingTenancyExceptionHandler missingTenancyExceptionHandler() {
        return new MissingTenancyExceptionHandler();
    }
}
