package io.casehub.platform.api.subscription;

import java.util.List;

/**
 * Partial update for subscriptions. All fields are nullable — null means "don't change".
 * Store implementations apply only non-null fields to the existing subscription.
 *
 * @param name        new subscription name (null = unchanged)
 * @param eventType   new CloudEvent type (null = unchanged)
 * @param constraints new filter constraints (null = unchanged)
 * @param targets     new notification targets (null = unchanged)
 * @param includeActor new includeActor flag (null = unchanged)
 * @param template    new notification template (null = unchanged)
 * @param enabled     new enabled state (null = unchanged)
 */
public record SubscriptionUpdate(
        String name,
        String eventType,
        List<Constraint> constraints,
        List<NotificationTarget> targets,
        Boolean includeActor,
        NotificationTemplate template,
        Boolean enabled
) {
    public SubscriptionUpdate {
        if (constraints != null) {
            constraints = List.copyOf(constraints);
        }
        if (targets != null) {
            targets = List.copyOf(targets);
        }
    }
}
