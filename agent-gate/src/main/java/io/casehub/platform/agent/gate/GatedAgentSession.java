package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.List;

final class GatedAgentSession implements AgentSession {

    private final AgentSession delegate;
    private final List<AdmissionStrategy> sessionStrategies;
    private final List<AdmissionStrategy> queryStrategies;
    private final Duration queryAcquireTimeout;

    GatedAgentSession(AgentSession delegate,
                      List<AdmissionStrategy> sessionStrategies,
                      List<AdmissionStrategy> queryStrategies,
                      Duration queryAcquireTimeout) {
        this.delegate = delegate;
        this.sessionStrategies = sessionStrategies;
        this.queryStrategies = queryStrategies;
        this.queryAcquireTimeout = queryAcquireTimeout;
    }

    @Override
    public Multi<AgentEvent> query(String prompt) {
        if (!queryStrategies.isEmpty()) {
            GatedAgentProvider.acquireAll(queryStrategies, queryAcquireTimeout);
        }
        return delegate.query(prompt);
    }

    @Override
    public Uni<Void> interrupt() {
        return delegate.interrupt();
    }

    @Override
    public void close(Duration maxWait) {
        try {
            delegate.close(maxWait);
        } finally {
            GatedAgentProvider.releaseAll(sessionStrategies);
        }
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(30));
    }
}
