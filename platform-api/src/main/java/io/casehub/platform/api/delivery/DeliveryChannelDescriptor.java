package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationSeverity;

import java.util.Objects;

/**
 * Descriptor for a delivery channel. Registered by {@link NotificationDeliverer}
 * implementations in the {@link DeliveryChannelRegistry} at startup.
 *
 * @param channelId               unique channel identifier (e.g., "in_app", "email")
 * @param displayName             user-facing channel name
 * @param external                whether channel is external (true) or in-app (false) — gates suppression
 * @param defaultEnabled          default enabled state when user has no preference
 * @param defaultMinSeverity      default minimum severity when user has no preference
 * @param defaultDigestSchedule   default digest schedule (null = immediate delivery)
 * @param guaranteedMinSeverity   minimum severity for retry on failure (null = no retry)
 */
public record DeliveryChannelDescriptor(
        String channelId,
        String displayName,
        boolean external,
        boolean defaultEnabled,
        NotificationSeverity defaultMinSeverity,
        DigestSchedule defaultDigestSchedule,
        NotificationSeverity guaranteedMinSeverity
) {
    public DeliveryChannelDescriptor {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(defaultMinSeverity, "defaultMinSeverity");
    }
}
