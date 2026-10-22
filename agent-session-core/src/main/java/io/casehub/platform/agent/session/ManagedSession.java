package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class ManagedSession implements AgentSession {
    private static final int CHARS_PER_TOKEN = 4;


    private final PooledSession pooledSession;
    private final String conversationId;
    private final SessionLifecycleManager manager;
    private final ClearingPolicy clearingPolicy;
    private final int clearAfterTurns;
    private final AtomicInteger turnCount = new AtomicInteger(0);
    private final SessionMetrics metrics;
    private final AtomicInteger  estimatedTokens = new AtomicInteger();


    ManagedSession(PooledSession pooledSession, String conversationId,
                   SessionLifecycleManager manager, ClearingPolicy clearingPolicy,
                   int clearAfterTurns, SessionMetrics metrics) {
        this.pooledSession   = pooledSession;
        this.conversationId  = conversationId;
        this.manager         = manager;
        this.clearingPolicy  = clearingPolicy;
        this.clearAfterTurns = clearAfterTurns;
        this.metrics         = metrics;
    }

    public String conversationId() { return conversationId; }
    public ClearingPolicy clearingPolicy() { return clearingPolicy; }
    public PooledSession pooledSession() { return pooledSession; }

    @Override
    public Multi<AgentEvent> query(String prompt) {
        int turn = turnCount.incrementAndGet();
        metrics.queryStarted();

        if (clearingPolicy == ClearingPolicy.EVERY_CALL && turn > 1) {
            pooledSession.delegate().clear();
            metrics.clearTriggered();
        } else if (clearingPolicy == ClearingPolicy.AFTER_N_TURNS
                   && clearAfterTurns > 0 && turn % clearAfterTurns == 0) {
            pooledSession.delegate().clear();
            metrics.clearTriggered();
        } else if (clearingPolicy == ClearingPolicy.SIZE_AWARE
                   && clearAfterTurns > 0 && estimatedTokens.get() > clearAfterTurns) {
            pooledSession.delegate().clear();
            estimatedTokens.set(0);
            metrics.clearTriggered();
        }

        if (clearingPolicy == ClearingPolicy.SIZE_AWARE) {
            estimatedTokens.addAndGet(prompt.length() / CHARS_PER_TOKEN);
            return pooledSession.delegate().query(prompt)
                                .onItem().invoke(event -> {
                        if (event instanceof AgentEvent.TextDelta td) {
                            estimatedTokens.addAndGet(td.text().length() / CHARS_PER_TOKEN);
                        }
                    });
        }

        return pooledSession.delegate().query(prompt);
    }

    @Override
    public Uni<Void> interrupt() {
        return pooledSession.interrupt();
    }

    @Override
    public void clear() {
        pooledSession.delegate().clear();
        turnCount.set(0);
        estimatedTokens.set(0);
        metrics.clearTriggered();
    }

    @Override
    public void close(Duration maxWait) {
        manager.release(conversationId);
    }

    @Override
    public void close() {
        close(Duration.ofSeconds(30));
    }

    int turnCount() { return turnCount.get(); }

    public SessionMetrics metrics() {return metrics;}

    public int estimatedTokens()    {return estimatedTokens.get();}


}
