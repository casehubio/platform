package io.casehub.platform.api.notification;

import java.time.Instant;
import java.util.Objects;

public record Notification(
        String id,
        String userId,
        String tenancyId,
        String title,
        String body,
        String category,
        NotificationSeverity severity,
        String actionUrl,
        NotificationSource source,
        NotificationStatus status,
        Instant createdAt,
        Instant readAt,
        Instant dismissedAt,
        ReplyHint replyHint
) {
    public Notification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
