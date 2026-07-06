package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.NotificationDeliverer;

import java.util.Objects;

/**
 * Result of channel routing — a channel resolved with its deliverer and suppression flag.
 *
 * @param channelId  channel identifier
 * @param deliverer  deliverer instance to use for delivery
 * @param suppressed whether delivery should be suppressed (external channel during snooze/quiet hours)
 */
public record ResolvedChannel(
        String channelId,
        NotificationDeliverer deliverer,
        boolean suppressed
) {
    public ResolvedChannel {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(deliverer, "deliverer");
    }
}
