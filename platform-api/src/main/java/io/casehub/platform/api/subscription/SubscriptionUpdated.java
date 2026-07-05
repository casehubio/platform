package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * CDI event fired after a subscription is updated.
 *
 * @param subscription the updated subscription (new state)
 * @param previous     the subscription before update (old state)
 */
public record SubscriptionUpdated(
        Subscription subscription,
        Subscription previous
) {
    public SubscriptionUpdated {
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(previous, "previous");
    }
}
