package io.casehub.platform.api.notification;

import java.util.Objects;

/**
 * Typed coordinates back to the originating event. Not a map — fixed structure,
 * compile-time type safety at every boundary.
 *
 * @param eventId    CloudEvent id — audit correlation
 * @param entityType domain entity kind (open set: "work-item", "case", etc.)
 * @param entityId   domain entity instance
 * @param actorId    who performed the action (originator, not recipient)
 */
public record NotificationSource(
        String eventId,
        String entityType,
        String entityId,
        String actorId
) {
    public NotificationSource {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(actorId, "actorId");
    }
}
