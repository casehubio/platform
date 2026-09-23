package io.casehub.platform.observability;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.smallrye.mutiny.Multi;

public class InstrumentedAgentBackend implements AgentBackend {

    private final AgentBackend delegate;
    private final Counter invocationCounter;
    private final Timer invocationTimer;
    private final Counter sessionCounter;

    public InstrumentedAgentBackend(AgentBackend delegate, MeterRegistry registry) {
        this.delegate = delegate;
        String backendKey = delegate.key();
        this.invocationCounter = Counter.builder("casehub.platform.agent.invocations")
                .tag("backend", backendKey)
                .register(registry);
        this.invocationTimer = Timer.builder("casehub.platform.agent.invocation.duration")
                .tag("backend", backendKey)
                .register(registry);
        this.sessionCounter = Counter.builder("casehub.platform.agent.sessions.opened")
                .tag("backend", backendKey)
                .register(registry);
    }

    @Override
    public String key() { return delegate.key(); }

    @Override
    public String instanceId() { return delegate.instanceId(); }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        return Multi.createFrom().deferred(() -> {
            invocationCounter.increment();
            Timer.Sample sample = Timer.start();
            return delegate.invoke(config)
                    .onTermination().invoke(() -> sample.stop(invocationTimer));
        });
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        sessionCounter.increment();
        Timer.Sample sample = Timer.start();
        try {
            return delegate.openSession(init);
        } finally {
            sample.stop(invocationTimer);
        }
    }
}
