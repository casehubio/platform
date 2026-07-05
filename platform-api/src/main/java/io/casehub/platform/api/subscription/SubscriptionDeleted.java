package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * CDI event fired after a subscription is deleted.
 *
 * @param subscription the deleted subscription
 */
public record SubscriptionDeleted(
        Subscription subscription
) {
    public SubscriptionDeleted {
        Objects.requireNonNull(subscription, "subscription");
    }
}
