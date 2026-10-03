package io.casehub.platform.agent.router;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingAgentProviderChainTest {

    private ModelDescriptor descriptor(String id, String backendKey, ModelTier tier) {
        return new ModelDescriptor(id, id, backendKey, "default",
                "anthropic", "claude", id, tier,
                Set.of(), 200000, 16000, ModelLocality.CLOUD, CostTier.HIGH, null, Map.of());
    }

    private ModelDescriptor descriptor(String id, String backendKey) {
        return descriptor(id, backendKey, ModelTier.FLAGSHIP);
    }

    private ModelRegistry registryWith(ModelDescriptor... descriptors) {
        return new ModelRegistry() {
            private final List<ModelDescriptor> all = List.of(descriptors);
            @Override public Optional<ModelDescriptor> resolveById(String id) {
                return all.stream().filter(d -> d.id().equals(id)).findFirst();
            }
            @Override public List<ModelDescriptor> query(ModelQuery query) {
                return all.stream()
                        .filter(d -> query.tier() == null || d.tier() == query.tier())
                        .filter(d -> query.vendor() == null || d.vendor().equals(query.vendor()))
                        .toList();
            }
            @Override public List<ModelDescriptor> all() { return all; }
        };
    }

    private BackendInstanceRegistry backendRegistry(AgentBackend... backends) {
        return new BackendInstanceRegistry() {
            @Override public void register(AgentBackend b) {}
            @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
                for (var b : backends) { if (b.key().equals(key)) return Optional.of(b); }
                return Optional.empty();
            }
            @Override public List<AgentBackend> resolveByKey(String key) {
                return java.util.Arrays.stream(backends).filter(b -> b.key().equals(key)).toList();
            }
        };
    }

    @Test
    void resolveChain_firstEntryResolves() {
        var claudeBackend = new StubBackend("claude");
        var registry = registryWith(descriptor("opus", "claude"), descriptor("sonnet", "claude"));
        var provider = new RoutingAgentProvider(backendRegistry(claudeBackend), "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);
        var config = AgentSessionConfig.of("sys", "user").withModelChain(ModelChain.of("opus", "sonnet"));
        assertThatCode(() -> provider.invoke(config)).doesNotThrowAnyException();
    }

    @Test
    void resolveChain_firstEntryMissing_fallsToSecond() {
        var claudeBackend = new StubBackend("claude");
        var registry = registryWith(descriptor("sonnet", "claude"));
        var provider = new RoutingAgentProvider(backendRegistry(claudeBackend), "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);
        var config = AgentSessionConfig.of("sys", "user").withModelChain(ModelChain.of("opus", "sonnet"));
        assertThatCode(() -> provider.invoke(config)).doesNotThrowAnyException();
    }

    @Test
    void resolveChain_allExhausted_throws() {
        var claudeBackend = new StubBackend("claude");
        var registry = registryWith();
        var provider = new RoutingAgentProvider(backendRegistry(claudeBackend), "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);
        var config = AgentSessionConfig.of("sys", "user").withModelChain(ModelChain.of("opus", "sonnet"));
        assertThatThrownBy(() -> provider.invoke(config))
                .isInstanceOf(ModelChainExhaustedException.class)
                .hasMessageContaining("2");
    }

    @Test
    void resolveChain_filterRejectsFirst_fallsToSecond() {
        var claudeBackend = new StubBackend("claude");
        var opus = descriptor("opus", "claude");
        var sonnet = descriptor("sonnet", "claude");
        var registry = registryWith(opus, sonnet);
        ModelAvailabilityFilter rejectOpus = d -> !d.id().equals("opus");
        var provider = new RoutingAgentProvider(backendRegistry(claudeBackend), "claude", registry,
                Map.of(), rejectOpus);
        var config = AgentSessionConfig.of("sys", "user").withModelChain(ModelChain.of("opus", "sonnet"));
        assertThatCode(() -> provider.invoke(config)).doesNotThrowAnyException();
    }

    @Test
    void resolveChain_mixedNamedAndQueried() {
        var claudeBackend = new StubBackend("claude");
        var opus = descriptor("opus", "claude", ModelTier.FLAGSHIP);
        var registry = registryWith(opus);
        var provider = new RoutingAgentProvider(backendRegistry(claudeBackend), "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);
        var entries = List.<ModelChain.ModelChainEntry>of(
                new ModelChain.ModelChainEntry.Queried(ModelQuery.builder().tier(ModelTier.EMBEDDING).build()),
                new ModelChain.ModelChainEntry.Named("opus"));
        var config = AgentSessionConfig.of("sys", "user").withModelChain(ModelChain.of(entries));
        assertThatCode(() -> provider.invoke(config)).doesNotThrowAnyException();
    }

    @Test
    void resolveChain_modelChainTakesPrecedenceOverModel() {
        var claudeBackend = new StubBackend("claude");
        var sonnet = descriptor("sonnet", "claude");
        var registry = registryWith(sonnet);
        var provider = new RoutingAgentProvider(backendRegistry(claudeBackend), "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);
        var config = new AgentSessionConfig("sys", "user", List.of(), null, null, "nonexistent",
                null, ModelChain.of("sonnet"));
        assertThatCode(() -> provider.invoke(config)).doesNotThrowAnyException();
    }

    @Test
    void openSession_usesChainResolution() {
        var claudeBackend = new StubBackend("claude");
        var sonnet = descriptor("sonnet", "claude");
        var registry = registryWith(sonnet);
        var provider = new RoutingAgentProvider(backendRegistry(claudeBackend), "claude", registry,
                Map.of(), ModelAvailabilityFilter.ALWAYS_AVAILABLE);
        var init = AgentSessionInit.of("prompt").withModelChain(ModelChain.of("nonexistent", "sonnet"));
        assertThatCode(() -> provider.openSession(init)).doesNotThrowAnyException();
    }

    private static class StubBackend implements AgentBackend {
        private final String backendKey;
        StubBackend(String key) { this.backendKey = key; }
        @Override public String key() { return backendKey; }
        @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) { return Multi.createFrom().empty(); }
        @Override public AgentSession openSession(AgentSessionInit init) {
            return new AgentSession() {
                @Override public Multi<AgentEvent> query(String prompt) { return Multi.createFrom().empty(); }
                @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
                @Override public void close(java.time.Duration maxWait) {}
            };
        }
    }
}
