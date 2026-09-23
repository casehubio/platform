package io.casehub.platform.spring.actuator;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBackendHealthIndicatorTest {

    @Test
    void upWhenBackendsPresent() {
        var indicator = new AgentBackendHealthIndicator(List.of(stubBackend("claude")));
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("backendCount", 1);
    }

    @Test
    void downWhenNoBackends() {
        var indicator = new AgentBackendHealthIndicator(List.of());
        var health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    private AgentBackend stubBackend(String key) {
        return new AgentBackend() {
            @Override public String key() { return key; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit i) { return null; }
        };
    }
}
