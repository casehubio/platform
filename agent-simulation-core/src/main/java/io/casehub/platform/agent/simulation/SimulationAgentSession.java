package io.casehub.platform.agent.simulation;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

class SimulationAgentSession implements AgentSession {

    private final String modelId;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    SimulationAgentSession(String modelId) {
        this.modelId = modelId != null ? modelId : "unknown";
    }

    @Override
    public Multi<AgentEvent> query(String userMessage) {
        if (closed.get()) {
            return Multi.createFrom().failure(
                    new IllegalStateException("Session is closed"));
        }
        return Multi.createFrom().items(
                new AgentEvent.TextDelta("Simulated response from " + modelId),
                new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, 0.0, 0L, 0L, null, 0, false)
        );
    }

    @Override
    public Uni<Void> interrupt() {
        return Uni.createFrom().voidItem();
    }

    @Override
    public void close(Duration timeout) {
        closed.set(true);
    }

    @Override
    public void clear() {}
}
