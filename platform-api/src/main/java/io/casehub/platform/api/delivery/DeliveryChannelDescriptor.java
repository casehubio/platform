package io.casehub.platform.api.delivery;

import io.casehub.platform.api.notification.NotificationSeverity;

import java.util.Objects;

public record DeliveryChannelDescriptor(
        String channelId,
        String displayName,
        boolean external,
        boolean defaultEnabled,
        NotificationSeverity defaultMinSeverity,
        DigestSchedule defaultDigestSchedule,
        NotificationSeverity guaranteedMinSeverity,
        DestinationScope destinationScope
) {
    public DeliveryChannelDescriptor {
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(defaultMinSeverity, "defaultMinSeverity");
        if (destinationScope == null) {destinationScope = DestinationScope.PER_USER;}
    }
}
