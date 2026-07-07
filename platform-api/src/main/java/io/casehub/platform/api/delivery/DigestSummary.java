package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationInput;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Digest summary for batch delivery.
 *
 * @param userId        recipient user ID
 * @param tenancyId     tenancy ID
 * @param channelId     delivery channel ID
 * @param notifications buffered notifications (must be non-empty)
 * @param periodStart   start of digest period
 * @param periodEnd     end of digest period
 * @param groupBy       grouping strategy (null = FLAT)
 */
public record DigestSummary(
        String userId, String tenancyId, String channelId,
        List<NotificationInput> notifications,
        Instant periodStart, Instant periodEnd,
        DigestGroupBy groupBy
) {
    public DigestSummary {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEnd, "periodEnd");
        if (notifications.isEmpty())
            throw new IllegalArgumentException("notifications must not be empty");
        notifications = List.copyOf(notifications);
    }
}
