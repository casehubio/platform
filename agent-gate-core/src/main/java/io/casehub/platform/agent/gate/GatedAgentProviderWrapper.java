package io.casehub.platform.agent.gate;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class GatedAgentProviderWrapper implements AgentProvider {

    private final AgentProvider delegate;
    private final SessionRegistry registry;
    private final List<AdmissionStrategy> strategies;
    private final List<AdmissionStrategy> sessionStrategies;
    private final List<AdmissionStrategy> invocationStrategies;
    private final Duration acquireTimeout;
    private final Duration queryAcquireTimeout;
    private final boolean active;

    public GatedAgentProviderWrapper(AgentProvider delegate,
                                     AgentGateProperties properties,
                                     SessionRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
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

        this.strategies = List.copyOf(built);
        this.sessionStrategies = built.stream()
                .filter(s -> s.scope() == AdmissionStrategy.Scope.SESSION)
                .toList();
        this.invocationStrategies = built.stream()
                .filter(s -> s.scope() == AdmissionStrategy.Scope.INVOCATION)
                .toList();
        this.active = !built.isEmpty();
    }

    @Override
    public Multi<AgentEvent> invoke(AgentSessionConfig config) {
        if (!active) {
            return delegate.invoke(config);
        }
        return Multi.createFrom().<AgentEvent>deferred(() -> {
            AdmissionUtils.acquireAll(strategies, acquireTimeout);
            try {
                Multi<AgentEvent> result = delegate.invoke(config);
                return result.onTermination()
                        .invoke(() -> AdmissionUtils.releaseAll(strategies));
            } catch (Exception e) {
                AdmissionUtils.releaseAll(strategies);
                throw e;
            }
        }).runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

    @Override
    public AgentSession openSession(AgentSessionInit init) {
        if (!active) {
            return delegate.openSession(init);
        }
        AdmissionUtils.acquireAll(strategies, acquireTimeout);
        try {
            AgentSession session = delegate.openSession(init);
            long id = registry.nextId();
            var gated = new GatedAgentSession(session, sessionStrategies,
                    invocationStrategies, queryAcquireTimeout, registry, id);
            registry.register(id, gated);
            return gated;
        } catch (Exception e) {
            AdmissionUtils.releaseAll(strategies);
            throw e;
        }
    }
}
