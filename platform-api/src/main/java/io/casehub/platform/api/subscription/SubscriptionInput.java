package io.casehub.platform.api.subscription;

import java.util.List;
import java.util.Objects;

/**
 * Routing layer input for creating a subscription. Store generates the id and
 * timestamps.
 *
 * @param ownerId     subscription owner (renamed from userId — who manages this subscription)
 * @param tenancyId   tenant isolation
 * @param name        user-facing subscription name
 * @param eventType   event type to match (single type, not multiple)
 * @param constraints filter constraints (AND semantics)
 * @param targets     who gets notified (explicit recipients, required non-empty)
 * @param includeActor include triggering actor in recipients (default false)
 * @param template    notification generation template
 * @param enabled     whether subscription is active
 */
public record SubscriptionInput(
        String ownerId,
        String tenancyId,
        String name,
        String eventType,
        List<Constraint> constraints,
        List<NotificationTarget> targets,
        boolean includeActor,
        NotificationTemplate template,
        boolean enabled
) {
    public SubscriptionInput {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(template, "template");
        constraints = List.copyOf(constraints);
        targets = List.copyOf(targets);
    }
}
