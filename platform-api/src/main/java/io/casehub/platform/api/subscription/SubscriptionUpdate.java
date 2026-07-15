package io.casehub.platform.api.subscription;

import io.casehub.platform.api.expression.ExpressionEvaluator;

import java.util.List;

public record SubscriptionUpdate(
        String name,
        String eventType,
        List<ExpressionEvaluator> filters,
        List<NotificationTarget> targets,
        Boolean includeActor,
        NotificationTemplate template,
        Boolean enabled
) {
    public SubscriptionUpdate {
        if (filters != null) {
            filters = List.copyOf(filters);
        }
        if (targets != null) {
            targets = List.copyOf(targets);
        }
    }
}
