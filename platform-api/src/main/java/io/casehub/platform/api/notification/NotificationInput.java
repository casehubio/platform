package io.casehub.platform.api.notification;

import java.util.Objects;

/**
 * What the routing layer passes to the store. No id, no status, no timestamps —
 * the store owns identity generation and lifecycle.
 *
 * @param userId     notification recipient
 * @param tenancyId  tenant isolation
 * @param title      notification title (always required)
 * @param body       notification body (nullable)
 * @param category   event type: "work-item.created", "sla.breached"
 * @param severity   visual priority level
 * @param actionUrl  deep link (nullable)
 * @param source     coordinates back to the originating event
 */
public record NotificationInput(
        String userId,
        String tenancyId,
        String title,
        String body,
        String category,
        NotificationSeverity severity,
        String actionUrl,
        NotificationSource source
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
