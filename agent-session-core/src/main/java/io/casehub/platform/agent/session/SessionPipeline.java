package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentRateLimitException;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.casehub.platform.agent.SessionRegistry;
import io.casehub.platform.agent.gate.AimdAdmissionStrategy;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

public class SessionPipeline implements SessionRegistry {

    private static final Logger LOG = Logger.getLogger(SessionPipeline.class);

    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(5);

    private final SessionLifecycleManager sessionManager;
    private final String backendKey;
    private final int clearAtTokens;
    private final Duration idleTimeout;
    private final AimdAdmissionStrategy admission;
    private final ScheduledExecutorService evictionScheduler;
    private final ConcurrentHashMap<String, ManagedSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastUsed = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentSessionInit> sessionInits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AimdAdmissionStrategy> companions = new ConcurrentHashMap<>();


    public SessionPipeline(SessionLifecycleManager sessionManager, String backendKey,
                           int ceiling, int clearAtTokens) {
        this(sessionManager, backendKey, ceiling, clearAtTokens, DEFAULT_IDLE_TIMEOUT);
    }

    public SessionPipeline(SessionLifecycleManager sessionManager, String backendKey,
                           int ceiling, int clearAtTokens, Duration idleTimeout) {
        this(sessionManager, backendKey,
             new AimdAdmissionStrategy(ceiling, 5, Duration.ofSeconds(10)),
             clearAtTokens, idleTimeout);
    }

    public SessionPipeline(SessionLifecycleManager sessionManager, String backendKey,
                           AimdAdmissionStrategy admission, int clearAtTokens) {
        this(sessionManager, backendKey, admission, clearAtTokens, DEFAULT_IDLE_TIMEOUT);
    }

    public SessionPipeline(SessionLifecycleManager sessionManager, String backendKey,
                           AimdAdmissionStrategy admission, int clearAtTokens, Duration idleTimeout) {
        this.sessionManager = sessionManager;
        this.backendKey = backendKey;
        this.admission = admission;
        this.clearAtTokens = clearAtTokens;
        this.idleTimeout = idleTimeout;
        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-pipeline-evictor-" + backendKey);
            t.setDaemon(true);
            return t;
        });
        long evictIntervalMs = Math.max(idleTimeout.toMillis() / 2, 50);
        evictionScheduler.scheduleWithFixedDelay(
                () -> evictIdleSessions(this.idleTimeout),
                evictIntervalMs, evictIntervalMs, TimeUnit.MILLISECONDS);
    }

    public ManagedSession openSession(String id, AgentSessionInit init) {
        return openSession(id, init, null);
    }

    public ManagedSession openSession(String id, AgentSessionInit init, Duration timeout) {
        sessionInits.put(id, init);

        ManagedSession session = sessions.computeIfAbsent(id, k ->
            sessionManager.acquire("pipeline:" + k, backendKey, init,
                    ClearingPolicy.SIZE_AWARE, clearAtTokens));

        lastUsed.put(id, Instant.now());
        return session;
    }

    public void releaseSession(String id) {
        ManagedSession session = sessions.remove(id);
        lastUsed.remove(id);
        sessionInits.remove(id);
        if (session != null) {
            try {
                sessionManager.release("pipeline:" + id);
            } catch (Exception e) {
                LOG.debugf("Failed to release session [id=%s]: %s", id, e.getMessage());
            }
        }
    }

    public int sessionCount() {
        return sessions.size();
    }

    public ManagedSession sessionFor(String id) {
        ManagedSession session = sessions.get(id);
        if (session == null) {
            AgentSessionInit init = sessionInits.get(id);
            if (init != null) {
                return openSession(id, init);
            }
        }
        return session;
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

    public <T> T executeSingle(String sessionId,
                               Function<AgentSession, T> queryFn,
                               Duration timeout) {
        try {
            if (!admission.tryAcquire(timeout)) {
                throw new AgentSessionLimitException(admission.activeCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during permit acquisition", e);
        }
        try {
            lastUsed.put(sessionId, Instant.now());
            T result = queryFn.apply(sessionFor(sessionId));
            admission.release();
            return result;
        } catch (Exception e) {
            if (isCapacityError(e)) {
                admission.rollback();
            } else {
                admission.release();
            }
            throw e;
        }
    }

    public <T> T executeSinglePriority(String sessionId,
                                        Function<AgentSession, T> queryFn,
                                        Duration timeout) {
        try {
            if (!admission.tryAcquirePriority(timeout)) {
                throw new AgentSessionLimitException(admission.activeCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during permit acquisition", e);
        }
        try {
            lastUsed.put(sessionId, Instant.now());
            T result = queryFn.apply(sessionFor(sessionId));
            admission.release();
            return result;
        } catch (Exception e) {
            if (isCapacityError(e)) {
                admission.rollback();
            } else {
                admission.release();
            }
            throw e;
        }
    }

    public void evictIdleSessions(Duration idleThreshold) {
        Instant cutoff = Instant.now().minus(idleThreshold);
        lastUsed.forEach((id, lastAccess) -> {
            if (lastAccess.isBefore(cutoff)) {
                ManagedSession session = sessions.remove(id);
                lastUsed.remove(id);
                if (session != null) {
                    try {
                        sessionManager.release("pipeline:" + id);
                    } catch (Exception e) {
                        LOG.debugf("Failed to evict session [id=%s]: %s", id, e.getMessage());
                    }
                }
                LOG.infof("Evicted idle session [id=%s, idle since %s]", id, lastAccess);
            }
        });
    }

    public int availablePermits() {
        return admission.availablePermits();
    }

    public AimdAdmissionStrategy admission() {
        return admission;
    }

    public AimdAdmissionStrategy companionAdmission(String name, int ceiling) {
        return companions.computeIfAbsent(name, k ->
                                                        new AimdAdmissionStrategy(ceiling, 5, Duration.ofSeconds(10)));
    }


    public static boolean isCapacityError(Exception e) {
        if (e instanceof SessionCapacityException) return true;
        if (e instanceof AgentRateLimitException) return true;
        if (e instanceof AgentSessionLimitException) return true;
        String msg = e.getMessage();
        if (msg != null) {
            String lower = msg.toLowerCase();
            if (lower.contains("429") || lower.contains("rate limit")) return true;
        }
        return false;
    }

    public void shutdown() {
        evictionScheduler.shutdownNow();
        sessions.forEach((id, session) -> {
            try {
                sessionManager.release("pipeline:" + id);
            } catch (Exception e) {
                LOG.debugf("Failed to release session on shutdown [id=%s]: %s", id, e.getMessage());
            }
        });
        sessions.clear();
        lastUsed.clear();
        sessionInits.clear();
    }

    private <R> R executeWithRetry(String id, BiFunction<String, ManagedSession, R> queryFn,
                                   int maxAttempts, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        ManagedSession session = sessions.get(id);
        if (session == null) {
            LOG.warnf("No session for identity '%s'", id);
            return null;
        }

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) { break; }

            try {
                if (!admission.tryAcquire(Duration.ofMillis(remaining))) {
                    LOG.debugf("%s: timeout waiting for capacity (attempt %d)", id, attempt + 1);
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            try {
                lastUsed.put(id, Instant.now());
                R result = queryFn.apply(id, session);
                admission.release();
                return result;
            } catch (Exception e) {
                if (isCapacityError(e)) {
                    admission.rollback();
                    LOG.debugf("%s: capacity exhausted (attempt %d/%d): %s",
                               id, attempt + 1, maxAttempts, e.getMessage());
                } else {
                    admission.release();
                    LOG.debugf("%s: query failed (attempt %d/%d): %s",
                               id, attempt + 1, maxAttempts, e.getMessage());
                }
            }
        }

        return null;
    }
}
