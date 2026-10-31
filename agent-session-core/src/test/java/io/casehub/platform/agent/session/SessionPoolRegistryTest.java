package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPoolRegistryTest {

    @Test
    void findPoolReturnsPoolForConfiguredBackend() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                1, 3, null, Map.of(), Map.of());
        var registry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());

        assertThat(registry.findPool("stub", null)).isPresent();
    }

    @Test
    void findPoolReturnsEmptyForUnconfiguredBackend() {
        var registry = new SessionPoolRegistry(List.of(), stubBackendRegistry());

        assertThat(registry.findPool("unknown", null)).isEmpty();
    }

    @Test
    void findPoolFallsBackToBackendKeyWhenModelNotFound() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                1, 3, null, Map.of(), Map.of());
        var registry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());

        assertThat(registry.findPool("stub", "some-model")).isPresent();
    }

    @Test
    void skipsPoolForUnknownBackend() {
        var decl = new PoolDeclaration("bad-pool", "test", "nonexistent",
                1, 3, null, Map.of(), Map.of());
        var registry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());

        assertThat(registry.findPool("nonexistent", null)).isEmpty();
    }


    @Test
    void findByName_returnsPoolByDeclaredName() {
        var decl = new PoolDeclaration("voice", "agent", "stub", 0, 5,
                                       null, Map.of(), Map.of(), 1, Map.of("policy", "every-call"), 0);
        var registry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());
        assertThat(registry.findByName("voice")).isPresent();
    }

    @Test
    void findByName_returnsEmptyForUnknownName() {
        var decl = new PoolDeclaration("voice", "agent", "stub", 0, 5,
                                       null, Map.of(), Map.of());
        var registry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());
        assertThat(registry.findByName("email")).isEmpty();
    }

    @Test
    void findByName_multiplePoolsSameBackend() {
        var voice = new PoolDeclaration("voice", "agent", "stub", 0, 10,
                                        null, Map.of(), Map.of(), 1, null, 0);
        var email = new PoolDeclaration("email", "agent", "stub", 0, 5,
                                        null, Map.of(), Map.of(), 0, null, 0);
        var registry = new SessionPoolRegistry(List.of(voice, email), stubBackendRegistry());
        assertThat(registry.findByName("voice")).isPresent();
        assertThat(registry.findByName("email")).isPresent();
        assertThat(registry.findByName("voice").get())
                .isNotSameAs(registry.findByName("email").get());
    }

    private static BackendInstanceRegistry stubBackendRegistry() {
        return new BackendInstanceRegistry() {
            private final AgentBackend stub = new SessionPoolTest.StubBackend();
            @Override public void register(AgentBackend backend) {}
            @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
                return "stub".equals(key) ? Optional.of(stub) : Optional.empty();
            }
            @Override public List<AgentBackend> resolveByKey(String key) {
                return "stub".equals(key) ? List.of(stub) : List.of();
            }
        };
    }
}
