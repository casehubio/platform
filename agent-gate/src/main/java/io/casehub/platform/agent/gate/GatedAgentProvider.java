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
import java.util.ArrayList;
import java.util.List;

@Decorator
@Priority(Interceptor.Priority.APPLICATION)
public class GatedAgentProvider implements AgentProvider {

    @Inject @Delegate @Any AgentProvider delegate;
    @Inject AgentGateProperties properties;
    @Inject SessionRegistry registry;

    private List<AdmissionStrategy> strategies = List.of();
    private List<AdmissionStrategy> sessionStrategies = List.of();
    private List<AdmissionStrategy> invocationStrategies = List.of();
    private Duration acquireTimeout;
    private Duration queryAcquireTimeout;
    private boolean active;

    protected GatedAgentProvider() {}

    GatedAgentProvider(AgentProvider delegate, List<AdmissionStrategy> strategies,
                       Duration acquireTimeout, Duration queryAcquireTimeout,
                       SessionRegistry registry) {
        this.delegate = delegate;
        this.acquireTimeout = acquireTimeout;
        this.queryAcquireTimeout = queryAcquireTimeout;
        this.registry = registry;
        setStrategies(strategies);
    }

    @PostConstruct
    void init() {
        if (properties == null) return;
        this.acquireTimeout = properties.acquireTimeout();
        this.queryAcquireTimeout = properties.queryAcquireTimeout();

        var built = new ArrayList<AdmissionStrategy>();
        var sw = properties.slidingWindow();
        if (sw.maxActions() > 0) {
            built.add(new SlidingWindowStrategy(sw.maxActions(),
                    Duration.ofSeconds(sw.windowSeconds())));
        }
        var tb = properties.tokenBucket();
        if (tb.permitsPerSecond() > 0) {
            int burst = tb.burstCapacity() > 0 ? tb.burstCapacity()
                    : (int) Math.ceil(tb.permitsPerSecond());
            built.add(new TokenBucketStrategy(tb.permitsPerSecond(), burst));
        }
        var cc = properties.concurrency();
        if (cc.max() > 0) {
            built.add(new ConcurrencyStrategy(cc.max()));
        }
        setStrategies(built);
    }

    private void setStrategies(List<AdmissionStrategy> all) {
        this.strategies = List.copyOf(all);
        this.sessionStrategies = all.stream()
                .filter(s -> s.scope() == AdmissionStrategy.Scope.SESSION)
                .toList();
        this.invocationStrategies = all.stream()
                .filter(s -> s.scope() == AdmissionStrategy.Scope.INVOCATION)
                .toList();
        this.active = !all.isEmpty();
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        if (!active) {
            return delegate.invoke(config);
        }
        return Multi.createFrom().<AgentEvent>deferred(() -> {
            acquireAll(strategies, acquireTimeout);
            try {
                Multi<AgentEvent> result = delegate.invoke(config);
                return result.onTermination()
                        .invoke(() -> releaseAll(strategies));
            } catch (Exception e) {
                releaseAll(strategies);
                throw e;
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        if (!active) {
            return delegate.openSession(init);
        }
        acquireAll(strategies, acquireTimeout);
        try {
            AgentSession session = delegate.openSession(init);
            long id = registry.nextId();
            var gated = new GatedAgentSession(session, sessionStrategies,
                    invocationStrategies, queryAcquireTimeout, registry, id);
            registry.register(id, gated);
            return gated;
        } catch (Exception e) {
            releaseAll(strategies);
            throw e;
        }
    }

    static void acquireAll(List<AdmissionStrategy> strategies,
                            Duration timeout) {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        for (int i = 0; i < strategies.size(); i++) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            Duration remaining = remainingNanos > 0
                    ? Duration.ofNanos(remainingNanos) : Duration.ZERO;
            try {
                if (!strategies.get(i).tryAcquire(remaining)) {
                    rollbackPrior(strategies, i);
                    throw exceptionFor(strategies.get(i));
                }
            } catch (InterruptedException e) {
                rollbackPrior(strategies, i);
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "Interrupted during admission acquisition", e);
            }
        }
    }

    static void releaseAll(List<AdmissionStrategy> strategies) {
        for (int i = strategies.size() - 1; i >= 0; i--) {
            strategies.get(i).release();
        }
    }

    private static void rollbackPrior(List<AdmissionStrategy> strategies,
                                       int failedIndex) {
        for (int j = failedIndex - 1; j >= 0; j--) {
            strategies.get(j).rollback();
        }
    }

    private static RuntimeException exceptionFor(AdmissionStrategy strategy) {
        if (strategy instanceof ConcurrencyStrategy) {
            return new AgentSessionLimitException(0);
        }
        return new AgentRateLimitException(0);
    }
}
