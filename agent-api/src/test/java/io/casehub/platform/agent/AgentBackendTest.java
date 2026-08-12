package io.casehub.platform.agent;

import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentBackendTest {

    @Test
    void backendHasKeyInvokeAndOpenSession() {
        AgentBackend backend = new AgentBackend() {
            @Override
            public String key() { return "test"; }

            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().empty();
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) { return null; }
        };
        assertThat(backend.key()).isEqualTo("test");
    }

    @Test
    void invokeReturnsStreamFromBackend() {
        AgentBackend backend = new AgentBackend() {
            @Override
            public String key() { return "stub"; }

            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().item(new AgentEvent.TextDelta("hello"));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) { return null; }
        };
        var events = backend.invoke(AgentSessionConfig.of("sys", "user"))
                .collect().asList().await().indefinitely();
        assertThat(events).hasSize(1);
        assertThat(((AgentEvent.TextDelta) events.get(0)).text()).isEqualTo("hello");
    }
}
