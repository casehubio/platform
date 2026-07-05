package io.casehub.platform.api.subscription;

import io.casehub.platform.api.notification.NotificationSeverity;

import java.util.Objects;

/**
 * Template for generating notifications from matched events. Field references
 * use curly-brace placeholders (e.g., "{entityId}") substituted from event POJO
 * properties at notification creation time.
 *
 * @param titlePattern       notification title with placeholders (required)
 * @param bodyPattern        notification body with placeholders (nullable)
 * @param severity           visual priority level
 * @param category           notification category (e.g., "work-item.created")
 * @param actionUrlPattern   deep link with placeholders (nullable)
 * @param entityType         entity type for NotificationSource
 * @param entityIdField      event property name for entity ID
 * @param actorIdField       event property name for actor ID
 */
public record NotificationTemplate(
        String titlePattern,
        String bodyPattern,
        NotificationSeverity severity,
        String category,
        String actionUrlPattern,
        String entityType,
        String entityIdField,
        String actorIdField
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
