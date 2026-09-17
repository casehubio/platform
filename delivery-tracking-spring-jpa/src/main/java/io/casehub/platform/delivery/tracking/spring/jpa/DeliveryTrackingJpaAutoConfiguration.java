package io.casehub.platform.delivery.tracking.spring.jpa;

import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Duration;

@AutoConfiguration
@ConditionalOnClass(SpringDeliveryAttemptStore.class)
@EnableJpaRepositories(basePackageClasses = DeliveryAttemptEntityRepository.class)
public class DeliveryTrackingJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DeliveryAttemptStore.class)
    public SpringDeliveryAttemptStore springDeliveryAttemptStore(
            DeliveryAttemptEntityRepository attemptRepo,
            EngagementEventEntityRepository engagementRepo,
            EntityManager entityManager,
            @Value("${casehub.delivery.retry.claim-timeout:5m}") Duration claimTimeout) {
        return new SpringDeliveryAttemptStore(attemptRepo, engagementRepo, entityManager, claimTimeout);
    }
}
