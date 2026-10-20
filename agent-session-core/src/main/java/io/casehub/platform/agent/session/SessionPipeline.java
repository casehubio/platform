package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentSessionInit;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

public class SessionPipeline {

    private static final Logger LOG = Logger.getLogger(SessionPipeline.class);

    private final SessionLifecycleManager sessionManager;
    private final String backendKey;
    private final int maxConcurrency;
    private final int clearAtTokens;
    private final Semaphore tickets;
    private final ConcurrentHashMap<String, ManagedSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService recovery;
    private final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    private volatile long recoveryIntervalMs = 10_000;

    public SessionPipeline(SessionLifecycleManager sessionManager, String backendKey,
                           int maxConcurrency, int clearAtTokens) {
        this.sessionManager = sessionManager;
        this.backendKey = backendKey;
        this.maxConcurrency = maxConcurrency;
        this.clearAtTokens = clearAtTokens;
        this.tickets = new Semaphore(maxConcurrency, true);
        this.recovery = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "pipeline-recovery");
            t.setDaemon(true);
            return t;
        });
        recovery.scheduleWithFixedDelay(this::injectRecoveryPermit, 1, 1, TimeUnit.SECONDS);
    }

    public ManagedSession openSession(String id, AgentSessionInit init) {
        return sessions.computeIfAbsent(id, k ->
                sessionManager.acquire("pipeline:" + k, backendKey, init,
                        ClearingPolicy.SIZE_AWARE, clearAtTokens));
    }

    public ManagedSession sessionFor(String id) {
        return sessions.get(id);
    }

    public <R> Map<String, R> executeBatch(Collection<String> ids,
                                           BiFunction<String, ManagedSession, R> queryFn,
                                           int maxAttempts,
                                           Duration timeout) {
        var successes = new ConcurrentHashMap<String, R>();
        var latch     = new CountDownLatch(ids.size());

        for (String id : ids) {
            Thread.ofVirtual().name("pipeline-" + id).start(() -> {
                try {
                    R result = executeWithRetry(id, queryFn, maxAttempts, timeout);
                    if (result != null) {
                        successes.put(id, result);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        var results = new java.util.HashMap<String, R>();
        for (String id : ids) {
            results.put(id, successes.get(id));
        }
        return results;
    }

    private <R> R executeWithRetry(String id, BiFunction<String, ManagedSession, R> queryFn,
                                   int maxAttempts, Duration timeout) {
        long           deadline = System.currentTimeMillis() + timeout.toMillis();
        ManagedSession session  = sessions.get(id);
        if (session == null) {
            LOG.warnf("No session for identity '%s'", id);
            return null;
        }

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {break;}

            try {
                if (!tickets.tryAcquire(remaining, TimeUnit.MILLISECONDS)) {
                    LOG.debugf("%s: timeout waiting for capacity (attempt %d)", id, attempt + 1);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            try {
                R result = queryFn.apply(id, session);
                lastSuccessTime.set(System.currentTimeMillis());
                tickets.release();
                return result;
            } catch (SessionCapacityException e) {
                // LLM capacity exhausted — don't release ticket (shrinks window)
                LOG.debugf("%s: capacity exhausted (attempt %d/%d): %s",
                           id, attempt + 1, maxAttempts, e.getMessage());
            } catch (Exception e) {
                // Non-capacity error (parse, timeout, etc.) — release ticket
                tickets.release();
                LOG.debugf("%s: query failed (attempt %d/%d): %s",
                           id, attempt + 1, maxAttempts, e.getMessage());
            }
        }

        return null;
    }

    private void injectRecoveryPermit() {
        if (System.currentTimeMillis() - lastSuccessTime.get() > recoveryIntervalMs) {
            if (tickets.availablePermits() < maxConcurrency) {
                tickets.release();
                LOG.debugf("Recovery: injected permit (available=%d)", tickets.availablePermits());
            }
        }
    }

    public void setRecoveryIntervalMs(long ms) {
        this.recoveryIntervalMs = ms;
    }

    public int availablePermits() {
        return tickets.availablePermits();
    }

    public void shutdown() {
        recovery.shutdownNow();
        sessions.forEach((id, session) -> {
            try {
                sessionManager.release("pipeline:" + id);
            } catch (Exception e) {
                LOG.debugf("Failed to release session on shutdown [id=%s]: %s", id, e.getMessage());
            }
        });
        sessions.clear();
    }
}
