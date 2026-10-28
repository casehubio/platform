package io.casehub.platform.governance.spring;

import io.casehub.platform.api.governance.TriageHandler;
import io.casehub.platform.governance.NoOpTriageHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class GovernanceTriageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    TriageHandler noOpTriageHandler() {
        return new NoOpTriageHandler();
    }
}
