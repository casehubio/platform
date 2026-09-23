package io.casehub.platform.observability;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.model.ModelRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.util.List;

public class PlatformGaugeBinder implements MeterBinder {

    private final ModelRegistry modelRegistry;
    private final DeliveryChannelRegistry channelRegistry;
    private final List<AgentBackend> backends;

    public PlatformGaugeBinder(ModelRegistry modelRegistry,
                                DeliveryChannelRegistry channelRegistry,
                                List<AgentBackend> backends) {
        this.modelRegistry = modelRegistry;
        this.channelRegistry = channelRegistry;
        this.backends = backends;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (modelRegistry != null) {
            Gauge.builder("casehub.platform.model.registry.size",
                    modelRegistry, r -> r.all().size())
                    .register(registry);
        }
        if (channelRegistry != null) {
            Gauge.builder("casehub.platform.delivery.channels.registered",
                    channelRegistry, r -> r.discover().size())
                    .register(registry);
        }
        if (backends != null) {
            Gauge.builder("casehub.platform.agent.backends.discovered",
                    backends, List::size)
                    .register(registry);
        }
    }
}
