package io.casehub.platform.subscription.spring.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.subscription.SubscriptionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringSubscriptionStore.class)
@EnableJpaRepositories(basePackageClasses = SubscriptionEntityRepository.class)
public class SubscriptionsJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SubscriptionStore.class)
    public SpringSubscriptionStore springSubscriptionStore(
            SubscriptionEntityRepository repo,
            ObjectMapper mapper,
            ApplicationEventPublisher events) {
        return new SpringSubscriptionStore(repo, mapper, events);
    }
}
