package io.casehub.platform.persistence.spring.mongodb;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.PreferenceStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringMongoPreferenceStore.class)
@EnableMongoRepositories(basePackageClasses = PreferenceDocumentRepository.class)
public class PersistenceMongoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PreferenceStore.class)
    public SpringMongoPreferenceStore springMongoPreferenceStore(
            PreferenceDocumentRepository repo,
            CurrentPrincipal principal,
            ApplicationEventPublisher events) {
        return new SpringMongoPreferenceStore(repo, principal, events);
    }

    @Bean
    @ConditionalOnMissingBean(PreferenceProvider.class)
    public SpringMongoPreferenceProvider springMongoPreferenceProvider(PreferenceDocumentRepository repo) {
        return new SpringMongoPreferenceProvider(repo);
    }
}
