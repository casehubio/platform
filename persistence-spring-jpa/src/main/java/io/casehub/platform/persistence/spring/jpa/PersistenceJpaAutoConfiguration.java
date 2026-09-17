package io.casehub.platform.persistence.spring.jpa;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.PreferenceStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringPreferenceStore.class)
@EnableJpaRepositories(basePackageClasses = PreferenceEntryRepository.class)
public class PersistenceJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PreferenceStore.class)
    public SpringPreferenceStore springPreferenceStore(
            PreferenceEntryRepository repo,
            CurrentPrincipal principal,
            ApplicationEventPublisher events) {
        return new SpringPreferenceStore(repo, principal, events);
    }

    @Bean
    @ConditionalOnMissingBean(PreferenceProvider.class)
    public SpringPreferenceProvider springPreferenceProvider(PreferenceEntryRepository repo) {
        return new SpringPreferenceProvider(repo);
    }
}
