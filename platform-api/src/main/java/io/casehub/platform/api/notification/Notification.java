package io.casehub.platform.api.notification;

import java.time.Instant;
import java.util.Objects;

/**
 * What the store persists and returns. Immutable — status transitions produce new
 * database state, not mutated instances.
 *
 * @param id           UUID v7 — time-ordered for cursor stability
 * @param userId       notification recipient
 * @param tenancyId    tenant isolation
 * @param title        notification title
 * @param body         notification body (nullable)
 * @param category     event type: "work-item.created", "sla.breached"
 * @param severity     visual priority level
 * @param actionUrl    deep link (nullable)
 * @param source       coordinates back to the originating event
 * @param status       current lifecycle status
 * @param createdAt    store-generated creation timestamp
 * @param readAt       set on markRead (nullable)
 * @param dismissedAt  set on dismiss (nullable)
 */
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
        Instant dismissedAt
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
