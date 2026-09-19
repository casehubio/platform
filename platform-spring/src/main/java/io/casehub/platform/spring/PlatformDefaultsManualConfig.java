package io.casehub.platform.spring;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.mock.MockCurrentPrincipal;
import io.casehub.platform.mock.MockPreferenceProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

@AutoConfiguration
public class PlatformDefaultsManualConfig {

    @Bean
    @ConditionalOnMissingBean(CurrentPrincipal.class)
    public MockCurrentPrincipal mockCurrentPrincipal(
            @Value("${casehub.platform.principal.actorId:system}") String actorId,
            @Value("${casehub.platform.principal.groups:}") List<String> groups,
            @Value("${casehub.tenancy.default-id:278776f9-e1b0-46fb-9032-8bddebdcf9ce}") String tenancyId,
            @Value("${casehub.platform.principal.crossTenantAdmin:false}") boolean crossTenantAdmin) {
        return new MockCurrentPrincipal(actorId, groups, tenancyId, crossTenantAdmin);
    }

    @Bean
    @ConditionalOnMissingBean(PreferenceProvider.class)
    public MockPreferenceProvider mockPreferenceProvider() {
        return new MockPreferenceProvider();
    }
}
