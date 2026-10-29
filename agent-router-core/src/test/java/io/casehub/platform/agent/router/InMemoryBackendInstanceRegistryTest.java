package io.casehub.platform.agent.router;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBackendInstanceRegistryTest {

    @Test
    void ofRegistersBackendsByKey() {
        var backend = stubBackend("ollama");
        var registry = InMemoryBackendInstanceRegistry.of(backend);

        assertThat(registry.resolve("ollama", "default")).isPresent();
        assertThat(registry.resolve("ollama", "default").get().key()).isEqualTo("ollama");
    }

    @Test
    void ofWithMultipleBackends() {
        var ollama = stubBackend("ollama");
        var claude = stubBackend("claude");
        var registry = InMemoryBackendInstanceRegistry.of(ollama, claude);

        assertThat(registry.resolve("ollama", "default")).isPresent();
        assertThat(registry.resolve("claude", "default")).isPresent();
        assertThat(registry.resolve("unknown", "default")).isEmpty();
    }

    @Test
    void resolveByKeyReturnsAllInstances() {
        var backend = stubBackend("ollama");
        var registry = InMemoryBackendInstanceRegistry.of(backend);

        assertThat(registry.resolveByKey("ollama")).hasSize(1);
        assertThat(registry.resolveByKey("unknown")).isEmpty();
    }

    @Test
    void ofWithNoBackendsCreatesEmptyRegistry() {
        var registry = InMemoryBackendInstanceRegistry.of();
        assertThat(registry.resolveByKey("any")).isEmpty();
    }

    @Test
    void keysReturnsAllRegisteredKeys() {
        var ollama = stubBackend("ollama");
        var claude = stubBackend("claude");
        var registry = InMemoryBackendInstanceRegistry.of(ollama, claude);

        assertThat(registry.keys()).containsExactlyInAnyOrder("ollama", "claude");
    }

    @Test
    void keysReturnsEmptyForEmptyRegistry() {
        var registry = InMemoryBackendInstanceRegistry.of();
        assertThat(registry.keys()).isEmpty();
    }

    private AgentBackend stubBackend(String key) {
        return new AgentBackend() {
            @Override public String key() { return key; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit init) { throw new UnsupportedOperationException(); }
        };
    }
}
