package io.casehub.platform.notification.settings;

import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * No-op {@link NotificationPreferenceStore} — active when no backend module is on the classpath.
 *
 * <p>{@link #get(String, String)} returns empty. {@link #update(String, String, NotificationPreferenceUpdate)}
 * returns a structurally valid {@link NotificationPreferences} record (current timestamp, empty
 * channelDefaults, no quiet hours) so callers that use the return value get valid data.
 *
 * <p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link NotificationPreferenceStore} implementation on the classpath, per the
 * {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpNotificationPreferenceStore implements NotificationPreferenceStore {

    @Override
    public Optional<NotificationPreferences> get(final String userId, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public NotificationPreferences update(final String userId, final String tenancyId, final NotificationPreferenceUpdate update) {
        return new NotificationPreferences(
                userId,
                tenancyId,
                Map.of(),
                null,
                Instant.now()
        );
    }
}
