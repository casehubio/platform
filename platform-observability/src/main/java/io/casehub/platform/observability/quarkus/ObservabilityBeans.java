package io.casehub.platform.observability.quarkus;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.observability.PlatformGaugeBinder;
import io.micrometer.core.instrument.binder.MeterBinder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class ObservabilityBeans {

    @Inject
    Instance<ModelRegistry> modelRegistry;

    @Inject
    Instance<DeliveryChannelRegistry> channelRegistry;

    @Inject
    Instance<AgentBackend> backends;

    @Produces
    @ApplicationScoped
    public MeterBinder platformGaugeBinder() {
        return new PlatformGaugeBinder(
                modelRegistry.isResolvable() ? modelRegistry.get() : null,
                channelRegistry.isResolvable() ? channelRegistry.get() : null,
                backends.isResolvable() ? backends.stream().toList() : null);
    }
}
