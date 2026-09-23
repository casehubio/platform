package io.casehub.platform.observability.quarkus;

import io.casehub.platform.api.delivery.DeliveryChannelRegistry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class DeliveryChannelHealthCheck implements HealthCheck {

    @Inject
    Instance<DeliveryChannelRegistry> registry;

    @Override
    public HealthCheckResponse call() {
        if (registry.isUnsatisfied()) {
            return HealthCheckResponse.up("deliveryChannels");
        }
        var channels = registry.get().discover();
        return HealthCheckResponse.named("deliveryChannels")
                .status(!channels.isEmpty())
                .withData("channelCount", channels.size())
                .build();
    }
}
