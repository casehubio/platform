package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * CDI event fired when a subscription matches an event.
 *
 * <p>Fired by {@code SubscriptionEngine} via {@code fireAsync()}.
 * Observed by {@code NotificationDispatcher}.
 *
 * @param subscription matched subscription
 * @param pojo         event POJO that matched
 */
public record SubscriptionMatched(
        Subscription subscription,
        Object pojo
) {
    public SubscriptionMatched {
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(pojo, "pojo");
    }
}
