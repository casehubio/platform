package io.casehub.platform.observability;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentedAgentBackendTest {

    private MeterRegistry registry;
    private StubAgentBackend delegate;
    private InstrumentedAgentBackend instrumented;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        delegate = new StubAgentBackend("test-backend");
        instrumented = new InstrumentedAgentBackend(delegate, registry);
    }

    @Test
    void invokeIncrementsCounterAndRecordsTimer() {
        var config = AgentSessionConfig.of("system", "user");
        instrumented.invoke(config).collect().asList().await().indefinitely();

        assertThat(registry.counter("casehub.platform.agent.invocations",
                "backend", "test-backend").count()).isEqualTo(1);
        assertThat(registry.timer("casehub.platform.agent.invocation.duration",
                "backend", "test-backend").count()).isEqualTo(1);
    }

    @Test
    void openSessionIncrementsCounter() {
        var init = AgentSessionInit.of("system");
        var session = instrumented.openSession(init);
        assertThat(session).isNotNull();

        assertThat(registry.counter("casehub.platform.agent.sessions.opened",
                "backend", "test-backend").count()).isEqualTo(1);
    }

    @Test
    void keyDelegatesToBackend() {
        assertThat(instrumented.key()).isEqualTo("test-backend");
    }

    @Test
    void timerMeasuresStreamDuration() {
        var config = AgentSessionConfig.of("system", "user");
        instrumented.invoke(config).collect().asList().await().indefinitely();

        var timer = registry.timer("casehub.platform.agent.invocation.duration",
                "backend", "test-backend");
        assertThat(timer.totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    }

    @Test
    void cancellationStopsTimer() {
        var cancelDelegate = new StubAgentBackend("test-backend") {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().emitter(em -> {
                    em.emit(new AgentEvent.TextDelta("hello"));
                });
            }
        };
        var cancelInstrumented = new InstrumentedAgentBackend(cancelDelegate, registry);
        var config = AgentSessionConfig.of("system", "user");

        cancelInstrumented.invoke(config)
                .select().first(1)
                .collect().asList()
                .await().indefinitely();

        assertThat(registry.timer("casehub.platform.agent.invocation.duration",
                "backend", "test-backend").count()).isEqualTo(1);
    }

    static class StubAgentBackend implements AgentBackend {
        private final String key;

        StubAgentBackend(String key) { this.key = key; }

        @Override public String key() { return key; }

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().items(new AgentEvent.TextDelta("response"));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            return new AgentSession() {
                @Override public Multi<AgentEvent> query(String prompt) {
                    return Multi.createFrom().empty();
                }
                @Override public Uni<Void> interrupt() { return Uni.createFrom().voidItem(); }
                @Override public void close(Duration maxWait) {}
                @Override public void close() {}
            };
        }
    }
}
