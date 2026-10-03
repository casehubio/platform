package io.casehub.platform.agent.router;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentRateLimitException;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.api.model.CostTier;
import io.casehub.platform.api.model.ModelAvailabilityFilter;
import io.casehub.platform.api.model.ModelChain;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelLocality;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.casehub.platform.api.model.ModelTier;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingAgentProviderRetryTest {

    private ModelDescriptor descriptor(String id) {
        return new ModelDescriptor(id, id, "claude", "default",
                "anthropic", "claude", id, ModelTier.FLAGSHIP,
                Set.of(), 200000, 16000, ModelLocality.CLOUD, CostTier.HIGH, null, Map.of());
    }

    private ModelRegistry registryWith(String... ids) {
        return new ModelRegistry() {
            private final List<ModelDescriptor> all = java.util.Arrays.stream(ids).map(id -> descriptor(id)).toList();
            @Override public Optional<ModelDescriptor> resolveById(String id) {
                return all.stream().filter(d -> d.id().equals(id)).findFirst();
            }
            @Override public List<ModelDescriptor> query(ModelQuery q) { return all; }
            @Override public List<ModelDescriptor> all() { return all; }
        };
    }

    @Test
    void invoke_withChain_firstSucceeds_noRetry() {
        var callCount = new AtomicInteger();
        var backend = new CountingBackend("claude", callCount, null);
        var registry = registryWith("opus", "sonnet");
        var backendReg = singleBackendRegistry(backend);
        var provider = new RoutingAgentProvider(backendReg, "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);

        var config = AgentSessionConfig.of("sys", "user")
                .withModelChain(ModelChain.of("opus", "sonnet"));

        var subscriber = provider.invoke(config)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        subscriber.awaitCompletion();

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void invoke_withChain_firstFailsWithRateLimit_retriesSecond() {
        var callCount = new AtomicInteger();
        var backend = new CountingBackend("claude", callCount, "opus");
        var registry = registryWith("opus", "sonnet");
        var backendReg = singleBackendRegistry(backend);
        var provider = new RoutingAgentProvider(backendReg, "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);

        var config = AgentSessionConfig.of("sys", "user")
                .withModelChain(ModelChain.of("opus", "sonnet"));

        var subscriber = provider.invoke(config)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        subscriber.awaitCompletion();

        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void invoke_withChain_allFail_throwsExhausted() {
        var callCount = new AtomicInteger();
        var backend = new AllFailBackend("claude", callCount);
        var registry = registryWith("opus", "sonnet");
        var backendReg = singleBackendRegistry(backend);
        var provider = new RoutingAgentProvider(backendReg, "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);

        var config = AgentSessionConfig.of("sys", "user")
                .withModelChain(ModelChain.of("opus", "sonnet"));

        var subscriber = provider.invoke(config)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        subscriber.awaitFailure();

        assertThat(subscriber.getFailure()).isInstanceOf(ModelChainExhaustedException.class);
        assertThat(callCount.get()).isEqualTo(2);
    }

    @Test
    void invoke_withChain_nonRetryableError_propagatesImmediately() {
        var backend = new NonRetryableFailBackend("claude");
        var registry = registryWith("opus", "sonnet");
        var backendReg = singleBackendRegistry(backend);
        var provider = new RoutingAgentProvider(backendReg, "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);

        var config = AgentSessionConfig.of("sys", "user")
                .withModelChain(ModelChain.of("opus", "sonnet"));

        var subscriber = provider.invoke(config)
                .subscribe().withSubscriber(AssertSubscriber.create(10));
        subscriber.awaitFailure();

        assertThat(subscriber.getFailure()).isInstanceOf(IllegalStateException.class);
        assertThat(subscriber.getFailure().getMessage()).contains("backend broken");
    }

    private BackendInstanceRegistry singleBackendRegistry(AgentBackend backend) {
        return new BackendInstanceRegistry() {
            @Override public void register(AgentBackend b) {}
            @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
                return key.equals(backend.key()) ? Optional.of(backend) : Optional.empty();
            }
            @Override public List<AgentBackend> resolveByKey(String key) {
                return key.equals(backend.key()) ? List.of(backend) : List.of();
            }
        };
    }

    private static class CountingBackend implements AgentBackend {
        private final String key;
        private final AtomicInteger callCount;
        private final String failModelId;

        CountingBackend(String key, AtomicInteger callCount, String failModelId) {
            this.key = key;
            this.callCount = callCount;
            this.failModelId = failModelId;
        }

        @Override public String key() { return key; }
        @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            callCount.incrementAndGet();
            if (failModelId != null && failModelId.equals(config.model())) {
                return Multi.createFrom().failure(new AgentRateLimitException(1.0));
            }
            return Multi.createFrom().empty();
        }
        @Override public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }

    private static class AllFailBackend implements AgentBackend {
        private final String key;
        private final AtomicInteger callCount;

        AllFailBackend(String key, AtomicInteger callCount) {
            this.key = key;
            this.callCount = callCount;
        }

        @Override public String key() { return key; }
        @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            callCount.incrementAndGet();
            return Multi.createFrom().failure(new AgentRateLimitException(1.0));
        }
        @Override public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }

    private static class NonRetryableFailBackend implements AgentBackend {
        private final String key;

        NonRetryableFailBackend(String key) { this.key = key; }

        @Override public String key() { return key; }
        @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().failure(new IllegalStateException("backend broken"));
        }
        @Override public AgentSession openSession(AgentSessionInit init) {
            throw new UnsupportedOperationException();
        }
    }
}
