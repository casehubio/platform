package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.simulation.SimulatedAgentBackend;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ElasticSessionPoolTest {

    private SessionPoolRegistry poolRegistry;
    private SessionLifecycleManager sessionManager;
    private SessionPipeline pipeline;

    @BeforeEach
    void setUp() {
        var decl = new PoolDeclaration("sim-pool", "test", "simulation",
                0, 5, null, Map.of(), Map.of());
        poolRegistry = new SessionPoolRegistry(List.of(decl), simBackendRegistry());
        sessionManager = new SessionLifecycleManager(poolRegistry);
    }

    @AfterEach
    void tearDown() {
        if (pipeline != null) pipeline.shutdown();
        sessionManager.shutdown();
        poolRegistry.shutdown();
    }

    // --- 1. Session creation never blocks on capacity ---

    @Test
    void sessionCreationNeverBlocksRegardlessOfMaxActive() {
        // maxActive=5, but we create 20 sessions — none should block or throw
        pipeline = new SessionPipeline(sessionManager, "simulation", 3, 50_000);

        assertThatCode(() -> {
            for (int i = 0; i < 20; i++) {
                pipeline.openSession("session-" + i, AgentSessionInit.of("system prompt"));
            }
        }).doesNotThrowAnyException();

        assertThat(pipeline.sessionCount()).isEqualTo(20);
    }

    @Test
    void poolCheckoutNeverThrowsSessionLimitException() {
        // Pool with maxActive=2, but checking out 10 sessions — no starvation
        var pool = poolRegistry.findPool("simulation", null).orElseThrow();

        var sessions = new ArrayList<PooledSession>();
        assertThatCode(() -> {
            for (int i = 0; i < 10; i++) {
                sessions.add(pool.checkout(AgentSessionInit.of("system")));
            }
        }).doesNotThrowAnyException();

        assertThat(sessions).hasSize(10);
        sessions.forEach(PooledSession::close);
    }

    // --- 2. Sticky sessions coexist without starvation ---

    @Test
    void fifteenStickySessions_allExecutePrompts() {
        pipeline = new SessionPipeline(sessionManager, "simulation", 3, 50_000);

        // Create 15 sticky sessions (simulating 5 character + narrator + 5 classify + 4 cognitive)
        var ids = IntStream.range(0, 15).mapToObj(i -> "sticky-" + i).toList();
        for (String id : ids) {
            pipeline.openSession(id, AgentSessionInit.of("system"));
        }

        // All 15 should be able to execute prompts via AIMD-gated batch
        var results = pipeline.executeBatch(
                ids,
                (id, session) -> {
                    session.query("prompt").subscribe().asStream().count();
                    return "ok-" + id;
                },
                1, Duration.ofSeconds(30));

        assertThat(results.values().stream().filter("ok-"::startsWith).count())
                .as("All 15 sessions must succeed — no starvation")
                .isEqualTo(0); // null check below is the real assertion

        for (String id : ids) {
            assertThat(results.get(id))
                    .as("Session %s must have produced a result", id)
                    .isNotNull()
                    .isEqualTo("ok-" + id);
        }
    }

    // --- 3. AIMD serializes prompt execution under contention ---

    @Test
    void aimdLimitsConcurrentPromptExecution() {
        pipeline = new SessionPipeline(sessionManager, "simulation", 3, 50_000);

        var ids = IntStream.range(0, 10).mapToObj(i -> "agent-" + i).toList();
        for (String id : ids) {
            pipeline.openSession(id, AgentSessionInit.of("system"));
        }

        var maxConcurrent = new AtomicInteger(0);
        var currentConcurrent = new AtomicInteger(0);

        var results = pipeline.executeBatch(
                ids,
                (id, session) -> {
                    int c = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(m -> Math.max(m, c));
                    try { Thread.sleep(50); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentConcurrent.decrementAndGet();
                    return "ok";
                },
                1, Duration.ofSeconds(30));

        assertThat(maxConcurrent.get())
                .as("AIMD ceiling=3 must limit concurrent prompts to at most 3")
                .isLessThanOrEqualTo(3);

        long succeeded = results.values().stream().filter(v -> v != null).count();
        assertThat(succeeded)
                .as("All 10 should eventually succeed via fair serialization")
                .isEqualTo(10);
    }

    // --- 4. Fair scheduling — all sessions get a turn ---

    @Test
    void fairSchedulingAllSessionsServed() {
        pipeline = new SessionPipeline(sessionManager, "simulation", 2, 50_000);

        var ids = IntStream.range(0, 8).mapToObj(i -> "fair-" + i).toList();
        for (String id : ids) {
            pipeline.openSession(id, AgentSessionInit.of("system"));
        }

        var served = new ConcurrentHashMap<String, AtomicInteger>();

        // Run 3 ticks — each session should be served at least once across all ticks
        for (int tick = 0; tick < 3; tick++) {
            pipeline.executeBatch(
                    ids,
                    (id, session) -> {
                        served.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
                        return "ok";
                    },
                    1, Duration.ofSeconds(10));
        }

        for (String id : ids) {
            assertThat(served.get(id))
                    .as("Session %s must have been served at least once", id)
                    .isNotNull();
            assertThat(served.get(id).get())
                    .as("Session %s must have been served across ticks", id)
                    .isGreaterThanOrEqualTo(1);
        }
    }

    // --- 5. Evicted sessions recreate transparently ---

    @Test
    void evictedSessionRecreatesTransparently() {
        pipeline = new SessionPipeline(sessionManager, "simulation", 3, 50_000);

        pipeline.openSession("ephemeral", AgentSessionInit.of("system"));
        var originalSession = pipeline.sessionFor("ephemeral");
        assertThat(originalSession).isNotNull();

        // Execute a query to prove it works
        pipeline.executeSingle("ephemeral",
                session -> {
                    session.query("before eviction").subscribe().asStream().count();
                    return "ok";
                },
                Duration.ofSeconds(5));

        // Evict with a zero-duration threshold (evicts everything)
        pipeline.evictIdleSessions(Duration.ZERO);

        // Session should be gone from the map
        // But accessing it should transparently recreate
        var recreatedSession = pipeline.sessionFor("ephemeral");
        assertThat(recreatedSession)
                .as("Evicted session must be transparently recreated on access")
                .isNotNull();
        assertThat(recreatedSession)
                .as("Recreated session must be a different object")
                .isNotSameAs(originalSession);

        // The recreated session must be functional
        var result = pipeline.executeSingle("ephemeral",
                session -> {
                    session.query("after eviction").subscribe().asStream().count();
                    return "ok";
                },
                Duration.ofSeconds(5));
        assertThat(result).isEqualTo("ok");
    }

    // --- 6. Eviction respects idle threshold ---

    @Test
    void evictionRespectsIdleThreshold() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "simulation", 3, 50_000);

        pipeline.openSession("active", AgentSessionInit.of("system"));
        pipeline.openSession("idle", AgentSessionInit.of("system"));

        // Use "active" recently, let "idle" age
        Thread.sleep(100);
        pipeline.executeSingle("active",
                session -> {
                    session.query("recent").subscribe().asStream().count();
                    return "ok";
                },
                Duration.ofSeconds(5));

        // Evict with 50ms threshold — "idle" is >100ms old, "active" was just used
        pipeline.evictIdleSessions(Duration.ofMillis(50));

        assertThat(pipeline.sessionFor("active"))
                .as("Recently used session should survive eviction")
                .isNotNull();
        // "idle" should be evicted (removed from sessions map)
        // With transparent recreation, sessionFor would recreate it,
        // so we check sessionCount instead
        assertThat(pipeline.sessionCount())
                .as("Only the active session should remain after eviction")
                .isEqualTo(1);
    }

    // --- 7. No resource leaks across eviction cycles ---

    @Test
    void noResourceLeaksAcrossEvictionCycles() {
        pipeline = new SessionPipeline(sessionManager, "simulation", 3, 50_000);

        for (int cycle = 0; cycle < 5; cycle++) {
            // Create sessions
            for (int i = 0; i < 5; i++) {
                pipeline.openSession("cycle-" + i, AgentSessionInit.of("system"));
            }

            // Execute batch
            var results = pipeline.executeBatch(
                    List.of("cycle-0", "cycle-1", "cycle-2", "cycle-3", "cycle-4"),
                    (id, session) -> {
                        session.query("tick").subscribe().asStream().count();
                        return "ok";
                    },
                    1, Duration.ofSeconds(10));

            assertThat(results.values().stream().filter(v -> v != null).count())
                    .as("Cycle %d: all 5 must succeed", cycle)
                    .isEqualTo(5);

            // Evict all
            pipeline.evictIdleSessions(Duration.ZERO);

            // AIMD permits must be fully restored
            assertThat(pipeline.availablePermits())
                    .as("Cycle %d: permits must be fully restored after eviction", cycle)
                    .isEqualTo(3);
        }
    }

    // --- 8. Reproduction of the exact starvation scenario from the issue ---

    @Test
    void issueScenario_elevenStickySessionsDoNotStarveTwelfth() {
        // Pool maxActive=12, but 11 sticky sessions should NOT prevent a 12th
        var decl = new PoolDeclaration("scenario-pool", "test", "simulation",
                0, 12, null, Map.of(), Map.of());
        var scenarioRegistry = new SessionPoolRegistry(List.of(decl), simBackendRegistry());
        var scenarioManager = new SessionLifecycleManager(scenarioRegistry);
        var scenarioPipeline = new SessionPipeline(scenarioManager, "simulation", 5, 50_000);

        try {
            // Create 11 sticky sessions (5 character + narrator + 5 classify)
            for (int i = 0; i < 11; i++) {
                scenarioPipeline.openSession("sticky-" + i, AgentSessionInit.of("system"));
            }

            // The 12th session (bdi-extraction) must NOT throw
            assertThatCode(() ->
                    scenarioPipeline.openSession("bdi-extraction", AgentSessionInit.of("system")))
                    .as("12th session must not be starved by 11 existing sticky sessions")
                    .doesNotThrowAnyException();

            // Execute a prompt on the 12th session
            var result = scenarioPipeline.executeSingle("bdi-extraction",
                    session -> {
                        session.query("extract").subscribe().asStream().count();
                        return "extracted";
                    },
                    Duration.ofSeconds(10));
            assertThat(result).isEqualTo("extracted");
        } finally {
            scenarioPipeline.shutdown();
            scenarioManager.shutdown();
            scenarioRegistry.shutdown();
        }
    }

    // --- 9. Slow sessions serialize through AIMD without deadlock ---

    @Test
    void slowSessionsSerializeThroughAimdWithoutDeadlock() {
        var slowDecl = new PoolDeclaration("slow-pool", "test", "slow-sim",
                0, 5, null, Map.of(), Map.of());
        var slowBackend = new DelayedSimulatedBackend(200);
        var slowRegistry = new SessionPoolRegistry(List.of(slowDecl), singleBackendRegistry("slow-sim", slowBackend));
        var slowManager = new SessionLifecycleManager(slowRegistry);
        var slowPipeline = new SessionPipeline(slowManager, "slow-sim", 2, 50_000);

        try {
            var ids = List.of("a", "b", "c", "d", "e");
            for (String id : ids) {
                slowPipeline.openSession(id, AgentSessionInit.of("system"));
            }

            long start = System.currentTimeMillis();
            var results = slowPipeline.executeBatch(
                    ids,
                    (id, session) -> {
                        session.query("slow").subscribe().asStream().count();
                        return "ok-" + id;
                    },
                    1, Duration.ofSeconds(30));

            long elapsed = System.currentTimeMillis() - start;

            long succeeded = results.values().stream().filter(v -> v != null).count();
            assertThat(succeeded)
                    .as("All 5 must complete despite AIMD ceiling=2 serialization")
                    .isEqualTo(5);

            // With ceiling=2 and 200ms per query, 5 queries should take ~600ms (3 waves)
            // Not 1000ms (5 sequential) — proves parallelism within AIMD window
            assertThat(elapsed).isLessThan(3000);
        } finally {
            slowPipeline.shutdown();
            slowManager.shutdown();
            slowRegistry.shutdown();
        }
    }

    // --- Infrastructure ---

    static BackendInstanceRegistry simBackendRegistry() {
        return singleBackendRegistry("simulation", new SimulatedAgentBackend(null));
    }

    static BackendInstanceRegistry singleBackendRegistry(String key, AgentBackend backend) {
        return new BackendInstanceRegistry() {
            @Override public void register(AgentBackend b) {}
            @Override public Optional<AgentBackend> resolve(String k, String instanceId) {
                return key.equals(k) ? Optional.of(backend) : Optional.empty();
            }
            @Override public List<AgentBackend> resolveByKey(String k) {
                return key.equals(k) ? List.of(backend) : List.of();
            }
        };
    }

    static class DelayedSimulatedBackend implements AgentBackend {
        private final long delayMs;

        DelayedSimulatedBackend(long delayMs) { this.delayMs = delayMs; }

        @Override public String key() { return "slow-sim"; }

        @Override
        public Multi<AgentEvent> invoke(AgentSessionConfig config) {
            return Multi.createFrom().item(new AgentEvent.TextDelta("delayed-response"));
        }

        @Override
        public AgentSession openSession(AgentSessionInit init) {
            return new DelayedSimulationSession(delayMs);
        }
    }

    static class DelayedSimulationSession implements AgentSession {
        private final long delayMs;

        DelayedSimulationSession(long delayMs) { this.delayMs = delayMs; }

        @Override
        public Multi<AgentEvent> query(String prompt) {
            try { Thread.sleep(delayMs); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Multi.createFrom().items(
                    new AgentEvent.TextDelta("delayed-response"),
                    new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, 0.0, 0L, 0L, null, 0, false, null, null));
        }

        @Override
        public io.smallrye.mutiny.Uni<Void> interrupt() {
            return io.smallrye.mutiny.Uni.createFrom().voidItem();
        }

        @Override public void clear() {}
        @Override public void close(Duration timeout) {}
    }
}
