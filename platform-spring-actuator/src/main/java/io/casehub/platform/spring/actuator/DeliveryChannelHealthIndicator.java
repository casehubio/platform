package io.casehub.platform.spring.actuator;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

class DeliveryChannelHealthIndicator implements HealthIndicator {

    private final DeliveryChannelRegistry registry;

    DeliveryChannelHealthIndicator(DeliveryChannelRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Health health() {
        var channels = registry.discover();
        if (channels.isEmpty()) {
            return Health.down().withDetail("channelCount", 0).build();
        }
        var ids = channels.stream().map(DeliveryChannelDescriptor::channelId).toList();
        return Health.up()
                .withDetail("channelCount", channels.size())
                .withDetail("channels", ids)
                .build();
    }
}
