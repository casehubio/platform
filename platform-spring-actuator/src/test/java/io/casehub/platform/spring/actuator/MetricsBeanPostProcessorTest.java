package io.casehub.platform.spring.actuator;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.observability.InstrumentedAgentBackend;
import io.casehub.platform.observability.InstrumentedAccessControlProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsBeanPostProcessorTest {

    @Test
    void wrapsAgentBackend() {
        var registry = new SimpleMeterRegistry();
        var bpp = new MetricsBeanPostProcessor(registry);
        var backend = stubBackend("test");

        var result = bpp.postProcessAfterInitialization(backend, "testBackend");

        assertThat(result).isInstanceOf(InstrumentedAgentBackend.class);
    }

    @Test
    void wrapsAccessControlProvider() {
        var registry = new SimpleMeterRegistry();
        var bpp = new MetricsBeanPostProcessor(registry);
        AccessControlProvider provider = new AccessControlProvider() {};

        var result = bpp.postProcessAfterInitialization(provider, "aclProvider");

        assertThat(result).isInstanceOf(InstrumentedAccessControlProvider.class);
    }

    @Test
    void doesNotDoubleWrap() {
        var registry = new SimpleMeterRegistry();
        var bpp = new MetricsBeanPostProcessor(registry);
        var backend = new InstrumentedAgentBackend(stubBackend("test"), registry);

        var result = bpp.postProcessAfterInitialization(backend, "testBackend");

        assertThat(result).isSameAs(backend);
    }

    @Test
    void ignoresUnrelatedBeans() {
        var registry = new SimpleMeterRegistry();
        var bpp = new MetricsBeanPostProcessor(registry);
        var unrelated = "just a string";

        var result = bpp.postProcessAfterInitialization(unrelated, "stringBean");

        assertThat(result).isSameAs(unrelated);
    }

    private AgentBackend stubBackend(String key) {
        return new AgentBackend() {
            @Override public String key() { return key; }
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig c) { return Multi.createFrom().empty(); }
            @Override public AgentSession openSession(AgentSessionInit i) { return null; }
        };
    }
}
