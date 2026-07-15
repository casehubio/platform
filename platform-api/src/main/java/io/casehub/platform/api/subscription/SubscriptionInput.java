package io.casehub.platform.api.subscription;

import io.casehub.platform.api.expression.ExpressionEvaluator;

import java.util.List;
import java.util.Objects;

public record SubscriptionInput(
        String ownerId,
        String tenancyId,
        String name,
        String eventType,
        List<ExpressionEvaluator> filters,
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
        Objects.requireNonNull(filters, "filters");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(template, "template");
        scope   = scope != null ? scope : SubscriptionScope.USER;
        filters = List.copyOf(filters);
        targets = List.copyOf(targets);
    }
}
