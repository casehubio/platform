package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentRateLimitException;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Decorator
@Priority(Interceptor.Priority.APPLICATION)
public class GatedAgentProvider implements AgentProvider {

    @Inject @Delegate @Any AgentProvider delegate;
    @Inject AgentGateProperties properties;

    private TokenBucket tokenBucket;
    private Semaphore concurrencyGate;
    private Duration acquireTimeout;
    private Duration queryAcquireTimeout;
    private boolean rateLimitActive;
    private boolean concurrencyActive;
    private boolean active;
    private double configuredPermitsPerSecond;
    private int configuredMaxConcurrent;

    protected GatedAgentProvider() {}

    GatedAgentProvider(AgentProvider delegate, int maxConcurrent,
                       double permitsPerSecond, int burstCapacity,
                       Duration acquireTimeout, Duration queryAcquireTimeout) {
        this.delegate = delegate;
        this.acquireTimeout = acquireTimeout;
        this.queryAcquireTimeout = queryAcquireTimeout;
        this.configuredPermitsPerSecond = permitsPerSecond;
        this.configuredMaxConcurrent = maxConcurrent;
        this.rateLimitActive = permitsPerSecond > 0;
        this.concurrencyActive = maxConcurrent > 0;
        this.active = rateLimitActive || concurrencyActive;
        if (rateLimitActive) {
            int burst = burstCapacity > 0 ? burstCapacity
                    : (int) Math.ceil(permitsPerSecond);
            this.tokenBucket = new TokenBucket(permitsPerSecond, burst);
        }
        if (concurrencyActive) {
            this.concurrencyGate = new Semaphore(maxConcurrent, true);
        }
    }

    @PostConstruct
    void init() {
        if (properties == null) return;
        int maxConcurrent = properties.maxConcurrent();
        double permitsPerSecond = properties.permitsPerSecond();
        int burstCapacity = properties.burstCapacity();
        this.acquireTimeout = properties.acquireTimeout();
        this.queryAcquireTimeout = properties.queryAcquireTimeout();
        this.configuredPermitsPerSecond = permitsPerSecond;
        this.configuredMaxConcurrent = maxConcurrent;

        if (maxConcurrent < 0) {
            throw new IllegalStateException(
                    "casehub.platform.agent.gate.max-concurrent must be >= 0");
        }
        if (permitsPerSecond < 0) {
            throw new IllegalStateException(
                    "casehub.platform.agent.gate.permits-per-second must be >= 0");
        }
        if (burstCapacity > 0 && permitsPerSecond <= 0) {
            throw new IllegalStateException(
                    "casehub.platform.agent.gate.burst-capacity > 0 requires "
                    + "permits-per-second > 0");
        }

        this.rateLimitActive = permitsPerSecond > 0;
        this.concurrencyActive = maxConcurrent > 0;
        this.active = rateLimitActive || concurrencyActive;

        if (rateLimitActive) {
            int burst = burstCapacity > 0 ? burstCapacity
                    : (int) Math.ceil(permitsPerSecond);
            this.tokenBucket = new TokenBucket(permitsPerSecond, burst);
        }
        if (concurrencyActive) {
            this.concurrencyGate = new Semaphore(maxConcurrent, true);
        }
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        if (!active) {
            return delegate.invoke(config);
        }
        return Multi.createFrom().<AgentEvent>deferred(() -> {
            long deadlineNanos = System.nanoTime() + acquireTimeout.toNanos();

            if (rateLimitActive) {
                try {
                    Duration remaining = durationUntil(deadlineNanos);
                    if (!tokenBucket.tryAcquire(remaining)) {
                        throw new AgentRateLimitException(configuredPermitsPerSecond);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            "Interrupted during rate limit acquisition", e);
                }
            }

            if (concurrencyActive) {
                try {
                    Duration remaining = durationUntil(deadlineNanos);
                    if (!concurrencyGate.tryAcquire(
                            remaining.toMillis(), TimeUnit.MILLISECONDS)) {
                        if (rateLimitActive) tokenBucket.release();
                        throw new AgentSessionLimitException(configuredMaxConcurrent);
                    }
                } catch (InterruptedException e) {
                    if (rateLimitActive) tokenBucket.release();
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(
                            "Interrupted during concurrency acquisition", e);
                }
            }

            try {
                Multi<AgentEvent> result = delegate.invoke(config);
                if (concurrencyActive) {
                    result = result.onTermination()
                            .invoke(() -> concurrencyGate.release());
                }
                return result;
            } catch (Exception e) {
                if (concurrencyActive) concurrencyGate.release();
                throw e;
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        if (!active) {
            return delegate.openSession(init);
        }
        long deadlineNanos = System.nanoTime() + acquireTimeout.toNanos();

        if (rateLimitActive) {
            try {
                Duration remaining = durationUntil(deadlineNanos);
                if (!tokenBucket.tryAcquire(remaining)) {
                    throw new AgentRateLimitException(configuredPermitsPerSecond);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "Interrupted during rate limit acquisition", e);
            }
        }

        if (concurrencyActive) {
            try {
                Duration remaining = durationUntil(deadlineNanos);
                if (!concurrencyGate.tryAcquire(
                        remaining.toMillis(), TimeUnit.MILLISECONDS)) {
                    if (rateLimitActive) tokenBucket.release();
                    throw new AgentSessionLimitException(configuredMaxConcurrent);
                }
            } catch (InterruptedException e) {
                if (rateLimitActive) tokenBucket.release();
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "Interrupted during concurrency acquisition", e);
            }
        }

        try {
            AgentSession session = delegate.openSession(init);
            return new GatedAgentSession(session, tokenBucket,
                    concurrencyGate, queryAcquireTimeout,
                    rateLimitActive, concurrencyActive,
                    configuredPermitsPerSecond);
        } catch (Exception e) {
            if (concurrencyActive) concurrencyGate.release();
            throw e;
        }
    }

    private static Duration durationUntil(long deadlineNanos) {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining > 0 ? Duration.ofNanos(remaining) : Duration.ZERO;
    }
}
