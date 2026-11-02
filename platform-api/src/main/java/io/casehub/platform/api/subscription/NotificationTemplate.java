package io.casehub.platform.api.subscription;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.ReplyHint;

import java.util.Objects;

public record NotificationTemplate(
        String titlePattern,
        String bodyPattern,
        NotificationSeverity severity,
        String category,
        String actionUrlPattern,
        String entityType,
        String entityIdField,
        String actorIdField,
        ReplyHint replyHint
) {
    public NotificationTemplate {
        Objects.requireNonNull(titlePattern, "titlePattern");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityIdField, "entityIdField");
        Objects.requireNonNull(actorIdField, "actorIdField");
    }
}
