package io.casehub.platform.api.subscription;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What the store persists and returns. Immutable — updates produce new database
 * state, not mutated instances.
 *
 * @param id          UUID v7 — time-ordered for cursor stability
 * @param userId      subscription owner
 * @param tenancyId   tenant isolation
 * @param name        user-facing subscription name
 * @param eventType   event type to match (single type, not multiple)
 * @param constraints filter constraints (AND semantics)
 * @param template    notification generation template
 * @param enabled     whether subscription is active
 * @param createdAt   store-generated creation timestamp
 * @param updatedAt   store-generated update timestamp
 */
public record Subscription(
        String id,
        String userId,
        String tenancyId,
        String name,
        String eventType,
        List<Constraint> constraints,
        NotificationTemplate template,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public Subscription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        constraints = List.copyOf(constraints);
    }
}
