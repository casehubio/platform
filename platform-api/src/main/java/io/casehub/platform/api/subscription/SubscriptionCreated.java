package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * CDI event fired after a subscription is created.
 *
 * @param subscription the newly created subscription
 */
public record SubscriptionCreated(
        Subscription subscription
) {
    public SubscriptionCreated {
        Objects.requireNonNull(subscription, "subscription");
    }
}
