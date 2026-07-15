package io.casehub.platform.api.subscription;

import io.casehub.platform.api.expression.ExpressionEvaluator;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Subscription(
        String id,
        String ownerId,
        String tenancyId,
        String name,
        String eventType,
        List<ExpressionEvaluator> filters,
        List<NotificationTarget> targets,
        boolean includeActor,
        NotificationTemplate template,
        boolean enabled,
        SubscriptionScope scope,
        Instant createdAt,
        Instant updatedAt
) {
    public Subscription {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(filters, "filters");
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        filters = List.copyOf(filters);
        targets = List.copyOf(targets);
    }
}
