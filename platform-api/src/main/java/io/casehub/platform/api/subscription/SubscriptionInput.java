package io.casehub.platform.api.subscription;

import java.util.List;
import java.util.Objects;

/**
 * Routing layer input for creating a subscription. Store generates the id and
 * timestamps.
 *
 * @param userId      subscription owner
 * @param tenancyId   tenant isolation
 * @param name        user-facing subscription name
 * @param eventType   event type to match (single type, not multiple)
 * @param constraints filter constraints (AND semantics)
 * @param template    notification generation template
 * @param enabled     whether subscription is active
 */
public record SubscriptionInput(
        String userId,
        String tenancyId,
        String name,
        String eventType,
        List<Constraint> constraints,
        NotificationTemplate template,
        boolean enabled
) {
    public SubscriptionInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(template, "template");
        constraints = List.copyOf(constraints);
    }
}
