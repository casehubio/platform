package io.casehub.platform.confirmation.spring;

import io.casehub.platform.api.confirmation.OperationConfirmationProvider;
import io.casehub.platform.confirmation.ConfirmationInterceptorCore;
import io.casehub.platform.confirmation.NoOpOperationConfirmationProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ConfirmationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OperationConfirmationProvider noOpConfirmationProvider() {
        return new NoOpOperationConfirmationProvider();
    }

    @Bean
    ConfirmationInterceptorCore confirmationInterceptorCore(
            OperationConfirmationProvider provider) {
        return new ConfirmationInterceptorCore(provider);
    }
}
