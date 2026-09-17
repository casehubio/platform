package io.casehub.platform.notification.settings.spring.jpa;

import io.casehub.platform.notification.settings.jpa.NotificationPreferencesEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferencesEntityRepository
        extends JpaRepository<NotificationPreferencesEntity, NotificationPreferencesEntity.PreferencesPK> {
}
