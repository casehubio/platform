package io.casehub.platform.observability;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannelRegistry;
import io.casehub.platform.api.delivery.NotificationDeliverer;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformGaugeBinderTest {

    @Test
    void registersModelRegistryGauge() {
        var registry = new SimpleMeterRegistry();
        var modelRegistry = new ModelRegistry() {
            @Override public Optional<ModelDescriptor> resolveById(String id) { return Optional.empty(); }
            @Override public List<ModelDescriptor> query(ModelQuery q) { return List.of(); }
            @Override public List<ModelDescriptor> all() {
                return List.of(
                    new ModelDescriptor("m1", "m1", "claude", null, "anthropic", "claude", "Claude Sonnet", ModelTier.FLAGSHIP, Set.of(), 200000, 8192, ModelLocality.CLOUD, null, null, null),
                    new ModelDescriptor("m2", "m2", "openai", null, "openai", "gpt", "GPT-4o", ModelTier.FLAGSHIP, Set.of(), 128000, 4096, ModelLocality.CLOUD, null, null, null)
                );
            }
        };

        new PlatformGaugeBinder(modelRegistry, null, null).bindTo(registry);

        assertThat(registry.get("casehub.platform.model.registry.size").gauge().value()).isEqualTo(2);
    }

    @Test
    void registersDeliveryChannelGauge() {
        var registry = new SimpleMeterRegistry();
        DeliveryChannelRegistry channelRegistry = new DeliveryChannelRegistry() {
            @Override public void register(DeliveryChannelDescriptor d, NotificationDeliverer del) {}
            @Override public Optional<DeliveryChannelDescriptor> resolve(String id) { return Optional.empty(); }
            @Override public Optional<NotificationDeliverer> resolveDeliverer(String id) { return Optional.empty(); }
            @Override public Set<DeliveryChannelDescriptor> discover() { return Set.of(); }
        };

        new PlatformGaugeBinder(null, channelRegistry, null).bindTo(registry);

        assertThat(registry.get("casehub.platform.delivery.channels.registered").gauge().value()).isEqualTo(0);
    }

    @Test
    void registersBackendGauge() {
        var registry = new SimpleMeterRegistry();
        var backends = List.<AgentBackend>of(
            stubBackend("claude"), stubBackend("openai"), stubBackend("gemini")
        );

        new PlatformGaugeBinder(null, null, backends).bindTo(registry);

        assertThat(registry.get("casehub.platform.agent.backends.discovered").gauge().value()).isEqualTo(3);
    }

    @Test
    void nullDependenciesSkipped() {
        var registry = new SimpleMeterRegistry();
        new PlatformGaugeBinder(null, null, null).bindTo(registry);
        assertThat(registry.getMeters()).isEmpty();
    }

    private AgentBackend stubBackend(String key) {
        return new AgentBackend() {
            @Override public String key() { return key; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit i) { return null; }
        };
    }
}
