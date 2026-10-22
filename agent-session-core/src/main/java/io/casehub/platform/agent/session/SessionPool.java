package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionPool {

    private static final Logger LOG = Logger.getLogger(SessionPool.class);

    private final AgentBackend             backend;
    private final SessionPoolConfig        config;
    private final ScheduledExecutorService scheduler;

    private final    BlockingDeque<WarmEntry> warm     = new LinkedBlockingDeque<>();
    private final    AtomicBoolean            shutdown = new AtomicBoolean(false);
    private volatile AgentSessionInit         lastInit;
    private final    SessionMetrics           metrics;


    record WarmEntry(AgentSession session, Instant returnedAt) {}

    public SessionPool(AgentBackend backend, SessionPoolConfig config) {
        this.backend   = backend;
        this.config    = config;
        this.metrics   = new SessionMetrics(config.name());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-pool-" + backend.key());
            t.setDaemon(true);
            return t;
        });
        long evictIntervalSecs = Math.max(config.idleTimeout().toSeconds() / 2, 10);
        scheduler.scheduleWithFixedDelay(this::evictIdle,
                                         evictIntervalSecs, evictIntervalSecs, TimeUnit.SECONDS);
    }

    public PooledSession checkout(AgentSessionInit init) {
        if (shutdown.get()) {
            throw new IllegalStateException("Pool is shut down");
        }

        lastInit = init;

        WarmEntry entry = warm.poll();
        if (entry != null) {
            metrics.sessionOpened();
            return new PooledSession(entry.session(), this, backend.key());
        }

        AgentSession session = backend.openSession(init);
        metrics.sessionOpened();
        return new PooledSession(session, this, backend.key());
    }

    public Multi<AgentEvent> invokeViaSession(AgentSessionConfig sessionConfig) {
        AgentSessionInit init = new AgentSessionInit(
                sessionConfig.systemPrompt(), sessionConfig.mcpServers(),
                sessionConfig.timeout(), sessionConfig.correlationId(),
                sessionConfig.model(), sessionConfig.modelQuery());

        return Multi.createFrom().deferred(() -> {
            PooledSession session = checkout(init);
            return session.delegate().query(sessionConfig.userPrompt())
                          .onCompletion().invoke(session::close)
                          .onFailure().invoke(t -> session.close())
                          .onCancellation().invoke(session::close);
        });
    }

    void release(PooledSession pooledSession) {
        metrics.sessionClosed();

        if (shutdown.get()) {
            pooledSession.destroyDelegate();
            return;
        }

        AgentSession delegate = pooledSession.delegate();

        try {
            delegate.clear();
            if (warm.size() < config.minActive()) {
                warm.offer(new WarmEntry(delegate, Instant.now()));
                return;
            }
        } catch (Exception e) {
            LOG.debugf("clear() failed, destroying session: %s", e.getMessage());
        }

        try {
            delegate.close();
        } catch (Exception e) {
            LOG.warnf("Failed to close session during release: %s", e.getMessage());
        }

        schedulePreWarm();
    }

    private void schedulePreWarm() {
        AgentSessionInit init = lastInit;
        if (init == null || shutdown.get()) {return;}
        if (warm.size() >= config.minActive()) {return;}

        scheduler.execute(() -> {
            if (shutdown.get() || warm.size() >= config.minActive()) {return;}
            try {
                AgentSession session = backend.openSession(init);
                warm.offer(new WarmEntry(session, Instant.now()));
                LOG.debugf("Pre-warmed session for backend %s (warm=%d)", backend.key(), warm.size());
            } catch (Exception e) {
                LOG.debugf("Pre-warm failed for backend %s: %s", backend.key(), e.getMessage());
            }
        });
    }

    private void evictIdle() {
        if (shutdown.get()) {return;}
        Instant     cutoff   = Instant.now().minus(config.idleTimeout());
        WarmEntry[] snapshot = warm.toArray(WarmEntry[]::new);
        for (WarmEntry entry : snapshot) {
            if (warm.size() <= config.minActive()) {break;}
            if (entry.returnedAt().isBefore(cutoff)) {
                if (warm.remove(entry)) {
                    try {
                        entry.session().close();
                    } catch (Exception e) {
                        LOG.debugf("Failed to close evicted session: %s", e.getMessage());
                    }
                    LOG.debugf("Evicted idle session for backend %s (warm=%d)", backend.key(), warm.size());
                }
            }
        }
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {return;}
        scheduler.shutdownNow();
        WarmEntry entry;
        while ((entry = warm.poll()) != null) {
            try {
                entry.session().close();
            } catch (Exception ignored) {}
        }
    }

    int warmCount()      {return warm.size();}

    public SessionMetrics metrics() {return metrics;}

}
