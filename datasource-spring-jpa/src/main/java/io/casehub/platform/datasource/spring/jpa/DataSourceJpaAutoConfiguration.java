package io.casehub.platform.datasource.spring.jpa;

import io.casehub.platform.api.datasource.DataSourceRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringDataSourceRegistry.class)
@EnableJpaRepositories(basePackageClasses = DataSourceDescriptorEntityRepository.class)
public class DataSourceJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataSourceRegistry.class)
    public SpringDataSourceRegistry springDataSourceRegistry(
            DataSourceDescriptorEntityRepository repo,
            ApplicationEventPublisher events) {
        return new SpringDataSourceRegistry(repo, events);
    }
}
