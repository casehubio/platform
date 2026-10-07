package io.casehub.platform.security.spring;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.identity.TenancyConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.annotation.RequestScope;

@AutoConfiguration
@ConditionalOnClass(SecurityContextHolder.class)
@AutoConfigureAfter(name = "io.casehub.platform.oidc.spring.OidcSpringAutoConfiguration")
@AutoConfigureBefore(name = "io.casehub.platform.spring.PlatformDefaultsManualConfig")
public class SecuritySpringAutoConfiguration {

    @Bean
    @RequestScope
    @ConditionalOnMissingBean(CurrentPrincipal.class)
    public SecurityContextCurrentPrincipal securityContextCurrentPrincipal(
            @Value("${casehub.tenancy.default-id:" + TenancyConstants.DEFAULT_TENANT_ID + "}") String defaultTenancyId) {
        return new SecurityContextCurrentPrincipal(defaultTenancyId);
    }
}
