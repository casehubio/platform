package io.casehub.platform.api.notification.settings;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Per-user notification preferences.
 *
 * @param userId          user identifier
 * @param tenancyId       tenant isolation
 * @param channelDefaults per-channel preferences keyed by channelId; absence = use platform default
 * @param quietHours      quiet hours configuration; nullable = no quiet hours
 * @param updatedAt       last update timestamp
 */
public record NotificationPreferences(
        String userId,
        String tenancyId,
        Map<String, ChannelPreference> channelDefaults,
        QuietHours quietHours,
        Instant updatedAt
) {
    public NotificationPreferences {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(channelDefaults, "channelDefaults");
        Objects.requireNonNull(updatedAt, "updatedAt");
        channelDefaults = Map.copyOf(channelDefaults);
    }
}
