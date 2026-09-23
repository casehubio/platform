package io.casehub.platform.observability.quarkus;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.observability.InstrumentedAgentBackend;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedAgentBackendTest {

    @Test
    void instrumentedBackendRecordsMetrics() {
        var registry = new SimpleMeterRegistry();
        AgentBackend delegate = new AgentBackend() {
            @Override public String key() { return "test"; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) {
                return Multi.createFrom().items(new AgentEvent.TextDelta("hi"));
            }
            @Override public AgentSession openSession(AgentSessionInit i) { return null; }
        };

        var instrumented = new InstrumentedAgentBackend(delegate, registry);
        instrumented.invoke(AgentSessionConfig.of("sys", "user"))
                .collect().asList().await().indefinitely();

        assertThat(registry.counter("casehub.platform.agent.invocations", "backend", "test").count())
                .isEqualTo(1);
    }
}
