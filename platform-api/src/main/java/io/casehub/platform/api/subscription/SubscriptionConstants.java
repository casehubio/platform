package io.casehub.platform.api.subscription;

import io.casehub.platform.api.path.Path;

/**
 * Constants for subscription SPI and notification routing.
 */
public final class SubscriptionConstants {

    /**
     * DataSource path for notification events. Subscription matcher publishes generated
     * notifications to this DataSource path for downstream consumption by NotificationStore.
     */
    public static final Path NOTIFICATION_DATASOURCE_PATH =
            Path.of("casehub", "platform", "notifications");

    private SubscriptionConstants() {
        // Utility class — no instances
    }
}
