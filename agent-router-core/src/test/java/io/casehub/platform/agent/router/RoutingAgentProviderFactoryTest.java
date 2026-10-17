package io.casehub.platform.agent.router;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.config.ManifestResult;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingAgentProviderFactoryTest {

    @Test
    void ofSingleBackend() {
        var provider = RoutingAgentProvider.of(stubBackend("ollama"));
        assertThat(provider).isNotNull();
    }

    @Test
    void ofMultipleBackends() {
        var provider = RoutingAgentProvider.of(
                stubBackend("ollama"), stubBackend("claude"));
        assertThat(provider).isNotNull();
    }

    @Test
    void ofWithManifest() {
        var manifest = ManifestResult.empty();
        var provider = RoutingAgentProvider.of(List.of(stubBackend("ollama")), manifest);
        assertThat(provider).isNotNull();
    }

    @Test
    void discoverWithEmptyDir(@TempDir Path tempDir) {
        var provider = RoutingAgentProvider.discover(tempDir);
        assertThat(provider).isNotNull();
    }

    @Test
    void discoverWithProfile(@TempDir Path tempDir) {
        var provider = RoutingAgentProvider.discover(tempDir, "test");
        assertThat(provider).isNotNull();
    }

    private AgentBackend stubBackend(String key) {
        return new AgentBackend() {
            @Override public String key() { return key; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().empty();
            }
            @Override public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
