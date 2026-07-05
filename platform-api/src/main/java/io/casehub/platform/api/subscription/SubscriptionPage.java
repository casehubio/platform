package io.casehub.platform.api.subscription;

import java.util.List;
import java.util.Objects;

/**
 * Page of subscriptions with optional next-page cursor.
 *
 * @param subscriptions subscriptions in this page (defensive copy made in constructor)
 * @param nextCursor    opaque cursor for the next page (null = no more pages)
 */
public record SubscriptionPage(
        List<Subscription> subscriptions,
        String nextCursor
) {
    public SubscriptionPage {
        Objects.requireNonNull(subscriptions, "subscriptions");
        subscriptions = List.copyOf(subscriptions);
    }
}
