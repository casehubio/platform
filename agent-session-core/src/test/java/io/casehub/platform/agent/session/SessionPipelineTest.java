package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPipelineTest {

    private SessionPoolRegistry poolRegistry;
    private SessionLifecycleManager sessionManager;
    private SessionPipeline pipeline;

    @BeforeEach
    void setUp() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                0, 10, null, Map.of(), Map.of());
        poolRegistry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());
        sessionManager = new SessionLifecycleManager(poolRegistry);
    }

    @AfterEach
    void tearDown() {
        if (pipeline != null) pipeline.shutdown();
        sessionManager.shutdown();
        poolRegistry.shutdown();
    }

    @Test
    void allSuccessReturnsAllResults() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        openSessions("a", "b", "c");

        var results = pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> "response-" + id,
                3, Duration.ofSeconds(10));

        assertThat(results).hasSize(3);
        assertThat(results.get("a")).isEqualTo("response-a");
        assertThat(results.get("b")).isEqualTo("response-b");
        assertThat(results.get("c")).isEqualTo("response-c");
        assertThat(pipeline.availablePermits()).isEqualTo(3);
    }

    @Test
    void failureThenSuccessTriggersRetry() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        openSessions("a", "b", "c");

        var callCount = new ConcurrentHashMap<String, AtomicInteger>();

        var results = pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> {
                    var count = callCount.computeIfAbsent(id, k -> new AtomicInteger());
                    int attempt = count.incrementAndGet();
                    if (id.equals("b") && attempt == 1) {
                        throw new RuntimeException("API overloaded");
                    }
                    return "response-" + id;
                },
                3, Duration.ofSeconds(10));

        assertThat(results.get("a")).isEqualTo("response-a");
        assertThat(results.get("b")).isEqualTo("response-b");
        assertThat(results.get("c")).isEqualTo("response-c");
        assertThat(callCount.get("b").get()).isEqualTo(2);
    }

    @Test
    void failureConsumesPermitReducingConcurrency() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        openSessions("a", "b", "c");

        var maxConcurrent = new AtomicInteger(0);
        var currentConcurrent = new AtomicInteger(0);
        var callCount = new ConcurrentHashMap<String, AtomicInteger>();

        pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> {
                    int c = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(m -> Math.max(m, c));
                    var count = callCount.computeIfAbsent(id, k -> new AtomicInteger());
                    int attempt = count.incrementAndGet();
                    try { Thread.sleep(50); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentConcurrent.decrementAndGet();
                    if (id.equals("b") && attempt == 1) {
                        throw new RuntimeException("overloaded");
                    }
                    return "ok";
                },
                3, Duration.ofSeconds(10));

        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(3);
        assertThat(callCount.get("b").get()).isEqualTo(2);
    }

    @Test
    void allFailuresReturnNullAfterMaxAttempts() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        pipeline.setRecoveryIntervalMs(100);
        openSessions("a", "b", "c");

        var results = pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> { throw new RuntimeException("total failure"); },
                3, Duration.ofSeconds(5));

        assertThat(results).hasSize(3);
        for (var entry : results.entrySet()) {
            assertThat(entry.getValue()).isNull();
        }
    }

    @Test
    void sessionsAreStickyPerIdentity() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        openSessions("a", "b");

        var sessionA = pipeline.sessionFor("a");
        var sessionB = pipeline.sessionFor("b");

        assertThat(sessionA).isNotNull();
        assertThat(sessionB).isNotNull();
        assertThat(sessionA).isNotSameAs(sessionB);
        assertThat(pipeline.sessionFor("a")).isSameAs(sessionA);
    }

    @Test
    void sessionsUseSizeAwareClearing() {
        pipeline = new SessionPipeline(sessionManager, "stub", 2, 50_000);
        openSessions("a");

        var session = pipeline.sessionFor("a");
        assertThat(session.clearingPolicy()).isEqualTo(ClearingPolicy.SIZE_AWARE);
    }

    @Test
    void recoveryTimerInjectsPermitAfterTotalFailure() {
        pipeline = new SessionPipeline(sessionManager, "stub", 2, 50_000);
        pipeline.setRecoveryIntervalMs(100);
        openSessions("a", "b");

        var callCount = new AtomicInteger();

        var results = pipeline.executeBatch(
                List.of("a", "b"),
                (id, session) -> {
                    int c = callCount.incrementAndGet();
                    if (c <= 4) {
                        throw new RuntimeException("initial failure");
                    }
                    return "recovered-" + id;
                },
                5, Duration.ofSeconds(10));

        long recovered = results.values().stream()
                .filter(v -> v != null && ((String) v).startsWith("recovered"))
                .count();
        assertThat(recovered).isGreaterThanOrEqualTo(1);
    }

    @Test
    void permitsRestoredAfterSuccessfulBatch() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        openSessions("a", "b", "c");

        pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> "ok",
                1, Duration.ofSeconds(5));

        assertThat(pipeline.availablePermits()).isEqualTo(3);

        pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> "ok-again",
                1, Duration.ofSeconds(5));

        assertThat(pipeline.availablePermits()).isEqualTo(3);
    }

    // --- Infrastructure ---

    private void openSessions(String... ids) {
        for (var id : ids) {
            pipeline.openSession(id, AgentSessionInit.of("test system prompt"));
        }
    }

    private static BackendInstanceRegistry stubBackendRegistry() {
        return new BackendInstanceRegistry() {
            private final AgentBackend stub = new SessionPoolTest.ClearableBackend();
            @Override public void register(AgentBackend backend) {}
            @Override public Optional<AgentBackend> resolve(String key, String instanceId) {
                return "stub".equals(key) ? Optional.of(stub) : Optional.empty();
            }
            @Override public List<AgentBackend> resolveByKey(String key) {
                return "stub".equals(key) ? List.of(stub) : List.of();
            }
        };
    }
}
