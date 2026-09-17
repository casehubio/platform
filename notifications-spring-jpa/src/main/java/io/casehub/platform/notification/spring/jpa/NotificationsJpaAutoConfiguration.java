package io.casehub.platform.notification.spring.jpa;

import io.casehub.platform.api.notification.NotificationStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringNotificationStore.class)
@EnableJpaRepositories(basePackageClasses = NotificationEntityRepository.class)
public class NotificationsJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(NotificationStore.class)
    public SpringNotificationStore springNotificationStore(
            NotificationEntityRepository repo,
            ApplicationEventPublisher events) {
        return new SpringNotificationStore(repo, events);
    }
}
