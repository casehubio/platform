package io.casehub.platform.acl.worker.spring;

import io.casehub.platform.acl.worker.WorkerCredentialValidator;
import io.casehub.platform.api.acl.WorkerCredentialStore;
import io.casehub.platform.api.identity.CurrentPrincipal;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(WorkerCredentialStore.class)
public class AclWorkerSpringAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WorkerCredentialValidator workerCredentialValidator(WorkerCredentialStore store) {
        return new WorkerCredentialValidator(store);
    }

    @Bean
    @ConditionalOnMissingBean(SpringWorkerScopeExtractor.class)
    public SpringWorkerScopeExtractor failClosedWorkerScopeExtractor() {
        return new FailClosedSpringWorkerScopeExtractor();
    }

    @Bean
    public WorkerCredentialSpringFilter workerCredentialSpringFilter(
            WorkerCredentialValidator validator,
            SpringWorkerScopeExtractor scopeExtractor,
            CurrentPrincipal currentPrincipal) {
        return new WorkerCredentialSpringFilter(validator, scopeExtractor, currentPrincipal);
    }
}
