package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentRateLimitException;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.concurrent.Semaphore;

final class GatedAgentSession implements AgentSession {

    private final AgentSession delegate;
    private final TokenBucket tokenBucket;
    private final Semaphore concurrencyGate;
    private final Duration queryAcquireTimeout;
    private final boolean rateLimitActive;
    private final boolean concurrencyActive;
    private final double configuredPermitsPerSecond;

    GatedAgentSession(AgentSession delegate,
                      TokenBucket tokenBucket,
                      Semaphore concurrencyGate,
                      Duration queryAcquireTimeout,
                      boolean rateLimitActive,
                      boolean concurrencyActive,
                      double configuredPermitsPerSecond) {
        this.delegate = delegate;
        this.tokenBucket = tokenBucket;
        this.concurrencyGate = concurrencyGate;
        this.queryAcquireTimeout = queryAcquireTimeout;
        this.rateLimitActive = rateLimitActive;
        this.concurrencyActive = concurrencyActive;
        this.configuredPermitsPerSecond = configuredPermitsPerSecond;
    }

    @Override
    public Multi<AgentEvent> query(String prompt) {
        if (rateLimitActive) {
            try {
                if (!tokenBucket.tryAcquire(queryAcquireTimeout)) {
                    return Multi.createFrom().failure(
                            new AgentRateLimitException(configuredPermitsPerSecond));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Multi.createFrom().failure(
                        new RuntimeException("Interrupted during rate limit acquisition", e));
            }
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
            if (concurrencyActive) {
                concurrencyGate.release();
            }
        }
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(30));
    }
}
