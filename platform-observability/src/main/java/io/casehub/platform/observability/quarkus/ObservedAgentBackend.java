package io.casehub.platform.observability.quarkus;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.observability.InstrumentedAgentBackend;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.mutiny.Multi;

import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.inject.Inject;

@Decorator
@Priority(1900)
public class ObservedAgentBackend implements AgentBackend {

    private final InstrumentedAgentBackend instrumented;

    @Inject
    ObservedAgentBackend(@Delegate AgentBackend delegate, MeterRegistry registry) {
        this.instrumented = new InstrumentedAgentBackend(delegate, registry);
    }

    @Override public String key() { return instrumented.key(); }
    @Override public String instanceId() { return instrumented.instanceId(); }
    @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) { return instrumented.invoke(config); }
    @Override public AgentSession openSession(AgentSessionInit init) { return instrumented.openSession(init); }
}
