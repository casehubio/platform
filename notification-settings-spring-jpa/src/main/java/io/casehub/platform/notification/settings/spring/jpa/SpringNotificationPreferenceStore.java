package io.casehub.platform.notification.settings.spring.jpa;

import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.casehub.platform.notification.settings.jpa.NotificationPreferencesEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class SpringNotificationPreferenceStore implements NotificationPreferenceStore {

    private final NotificationPreferencesEntityRepository repo;

    public SpringNotificationPreferenceStore(NotificationPreferencesEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<NotificationPreferences> get(String userId, String tenancyId) {
        var pk = new NotificationPreferencesEntity.PreferencesPK(userId, tenancyId);
        return repo.findById(pk).map(NotificationPreferencesEntity::toPreferences);
    }

    @Override
    @Transactional
    public NotificationPreferences update(String userId, String tenancyId, NotificationPreferenceUpdate update) {
        var pk = new NotificationPreferencesEntity.PreferencesPK(userId, tenancyId);
        NotificationPreferencesEntity existing = repo.findById(pk).orElse(null);

        NotificationPreferencesEntity entity = NotificationPreferencesEntity.fromUpdate(
                userId, tenancyId, update, existing);

        NotificationPreferencesEntity saved = repo.save(entity);
        return saved.toPreferences();
    }
}
