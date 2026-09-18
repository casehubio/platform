package io.casehub.platform.agent.router;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.ManifestResult;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingAgentProviderCreateTest {

    private final StubBackend stubBackend = new StubBackend();
    private final BackendInstanceRegistry registry = new BackendInstanceRegistry() {
        @Override public void register(AgentBackend backend) {}
        @Override public Optional<AgentBackend> resolve(String key, String instanceId) { return Optional.of(stubBackend); }
        @Override public java.util.List<AgentBackend> resolveByKey(String key) { return java.util.List.of(stubBackend); }
    };
    private final ModelRegistry modelRegistry = new EmptyModelRegistry();

    @Test
    void create_withManifestResult_usesManifestDefaultAndAliases() {
        var aliases = Map.of("fast", ModelQuery.builder().build());
        var manifest = new ManifestResult(aliases, "custom-backend");

        var provider = RoutingAgentProvider.create(
                registry, "fallback-backend", modelRegistry, Optional.of(manifest));

        assertThat(provider).isNotNull();
    }

    @Test
    void create_withManifestResultNullDefaultKey_usesConfigDefault() {
        var manifest = new ManifestResult(Map.of(), null);

        var provider = RoutingAgentProvider.create(
                registry, "config-default", modelRegistry, Optional.of(manifest));

        assertThat(provider).isNotNull();
    }

    @Test
    void create_withoutManifestResult_usesConfigDefault() {
        var provider = RoutingAgentProvider.create(
                registry, "config-default", modelRegistry, Optional.empty());

        assertThat(provider).isNotNull();
    }

    private static class StubBackend implements AgentBackend {
        @Override public String key() { return "stub"; }
        @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) { return Multi.createFrom().empty(); }
        @Override public AgentSession openSession(AgentSessionInit init) { throw new UnsupportedOperationException(); }
    }

    private static class EmptyModelRegistry implements ModelRegistry {
        @Override public Optional<io.casehub.platform.api.model.ModelDescriptor> resolveById(String id) { return Optional.empty(); }
        @Override public java.util.List<io.casehub.platform.api.model.ModelDescriptor> query(ModelQuery query) { return java.util.List.of(); }
        @Override public java.util.List<io.casehub.platform.api.model.ModelDescriptor> all() { return java.util.List.of(); }
    }
}
