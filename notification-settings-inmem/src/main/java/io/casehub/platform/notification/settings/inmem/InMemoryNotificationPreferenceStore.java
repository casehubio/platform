package io.casehub.platform.notification.settings.inmem;

import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Volatile in-memory {@link NotificationPreferenceStore}.
 *
 * <p>Tier 4 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * JPA (Tier 2) and NoOp (Tier 1) when on the classpath.
 *
 * <p>Thread-safe. Data is ephemeral (lost on restart). Suitable for tests and
 * zero-config ephemeral single-node installs. Do NOT combine with
 * notification-settings-jpa in the same deployment scope.
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryNotificationPreferenceStore implements NotificationPreferenceStore {

    private final ConcurrentHashMap<String, NotificationPreferences> store = new ConcurrentHashMap<>();

    @Override
    public Optional<NotificationPreferences> get(String userId, String tenancyId) {
        String key = makeKey(userId, tenancyId);
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public NotificationPreferences update(String userId, String tenancyId, NotificationPreferenceUpdate update) {
        String key = makeKey(userId, tenancyId);
        Instant now = Instant.now();

        return store.compute(key, (k, existing) -> {
            if (existing == null) {
                // Create new preferences
                return new NotificationPreferences(
                        userId,
                        tenancyId,
                        update.channelDefaults() != null ? update.channelDefaults() : java.util.Map.of(),
                        update.clearQuietHours() ? null : update.quietHours(),
                        now
                );
            } else {
                // Update existing preferences
                return new NotificationPreferences(
                        userId,
                        tenancyId,
                        update.channelDefaults() != null ? update.channelDefaults() : existing.channelDefaults(),
                        update.clearQuietHours() ? null :
                                (update.quietHours() != null ? update.quietHours() : existing.quietHours()),
                        now
                );
            }
        });
    }

    private String makeKey(String userId, String tenancyId) {
        return userId + ":" + tenancyId;
    }

    /**
     * Clears all stored preferences. For test use only.
     */
    public void clear() {
        store.clear();
    }
}
