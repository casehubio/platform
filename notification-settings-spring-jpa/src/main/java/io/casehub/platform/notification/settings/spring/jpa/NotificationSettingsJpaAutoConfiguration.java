package io.casehub.platform.notification.settings.spring.jpa;

import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(SpringNotificationPreferenceStore.class)
@EnableJpaRepositories(basePackageClasses = NotificationPreferencesEntityRepository.class)
public class NotificationSettingsJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(NotificationPreferenceStore.class)
    public SpringNotificationPreferenceStore springNotificationPreferenceStore(
            NotificationPreferencesEntityRepository repo) {
        return new SpringNotificationPreferenceStore(repo);
    }

    @Bean
    @ConditionalOnMissingBean(SuppressionStore.class)
    public SpringSuppressionStore springSuppressionStore(
            MuteRuleEntityRepository muteRepo,
            SnoozeEntityRepository snoozeRepo) {
        return new SpringSuppressionStore(muteRepo, snoozeRepo);
    }
}
