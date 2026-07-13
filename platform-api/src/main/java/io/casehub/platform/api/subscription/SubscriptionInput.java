package io.casehub.platform.api.subscription;

import java.util.List;
import java.util.Objects;

public record SubscriptionInput(
        String ownerId,
        String tenancyId,
        String name,
        String eventType,
        List<Constraint> constraints,
        List<NotificationTarget> targets,
        boolean includeActor,
        NotificationTemplate template,
        boolean enabled,
        SubscriptionScope scope
) {
    public SubscriptionInput {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(template, "template");
        scope       = scope != null ? scope : SubscriptionScope.USER;
        constraints = List.copyOf(constraints);
        targets     = List.copyOf(targets);
    }
}
