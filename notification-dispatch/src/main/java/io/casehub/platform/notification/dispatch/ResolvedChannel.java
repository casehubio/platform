package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.notification.NotificationSeverity;

import java.util.Objects;

/**
 * Result of channel routing — a channel resolved with its deliverer, suppression flag, and digest flag.
 *
 * @param channelId                channel identifier
 * @param deliverer                deliverer instance to use for delivery
 * @param suppressed               whether delivery should be suppressed (external channel during snooze/quiet hours)
 * @param digested                 whether delivery should be queued for digest (external + schedule + non-URGENT)
 * @param guaranteedMinSeverity    minimum severity for retry on failure (null = no retry)
 */
public record ResolvedChannel(
        String channelId,
        NotificationDeliverer deliverer,
        boolean suppressed,
        boolean digested,
        NotificationSeverity guaranteedMinSeverity
) {
    public ResolvedChannel {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(deliverer, "deliverer");
    }
}
