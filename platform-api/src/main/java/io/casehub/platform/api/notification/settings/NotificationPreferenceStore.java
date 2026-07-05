package io.casehub.platform.api.notification.settings;

import java.util.Optional;

/**
 * SPI for storing per-user notification preferences.
 *
 * <p>No reactive variant — blocking on managed executor is acceptable. Future
 * reactive addition via default methods per {@code spi-evolution-default-methods}
 * protocol.
 */
public interface NotificationPreferenceStore {

    /**
     * Get user preferences.
     *
     * @param userId    user identifier
     * @param tenancyId tenant isolation
     * @return preferences if stored, empty otherwise
     */
    Optional<NotificationPreferences> get(String userId, String tenancyId);

    /**
     * Update user preferences. Upsert — creates if absent, updates if present.
     *
     * @param userId    user identifier
     * @param tenancyId tenant isolation
     * @param update    preference update
     * @return updated preferences
     */
    NotificationPreferences update(String userId, String tenancyId, NotificationPreferenceUpdate update);
}
