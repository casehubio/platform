package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationInput;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DigestSummary(
        String userId, String tenancyId, String channelId,
        List<NotificationInput> notifications,
        Instant periodStart, Instant periodEnd
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
