package io.casehub.platform.api.notification;

import java.util.Objects;

public record NotificationInput(
        String userId,
        String tenancyId,
        String title,
        String body,
        String category,
        NotificationSeverity severity,
        String actionUrl,
        NotificationSource source,
        ReplyHint replyHint
) {
    public NotificationInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(source, "source");
    }
}
