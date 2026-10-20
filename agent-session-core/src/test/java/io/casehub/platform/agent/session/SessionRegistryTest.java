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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SessionRegistryTest {

    private SessionPoolRegistry poolRegistry;
    private SessionLifecycleManager sessionManager;
    private SessionPipeline pipeline;

    @BeforeEach
    void setUp() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                0, 20, null, Map.of(), Map.of());
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
    void openSessionSucceedsUnderLimit() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 5, 50_000);

        for (String id : List.of("a", "b", "c")) {
            assertThat(pipeline.openSession(id, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(3);
    }

    @Test
    void openSessionBlocksAtLimit() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 2, 50_000);

        pipeline.openSession("a", init());
        pipeline.openSession("b", init());
        assertThat(pipeline.sessionCount()).isEqualTo(2);

        var result = pipeline.openSession("c", init(), Duration.ofMillis(100));
        assertThat(result).isNull();
        assertThat(pipeline.sessionCount()).isEqualTo(2);
    }

    @Test
    void releaseSessionFreesCapacity() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 2, 50_000);
        pipeline.openSession("a", init());
        pipeline.openSession("b", init());

        assertThat(pipeline.openSession("c", init(), Duration.ofMillis(100))).isNull();

        pipeline.releaseSession("a");
        assertThat(pipeline.sessionCount()).isEqualTo(1);

        assertThat(pipeline.openSession("c", init())).isNotNull();
        assertThat(pipeline.sessionCount()).isEqualTo(2);
    }

    @Test
    void duplicateOpenReturnsSameSessionWithoutConsumingSlot() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 3, 50_000);

        var session = pipeline.openSession("a", init());
        var same = pipeline.openSession("a", init());

        assertThat(same).isSameAs(session);
        assertThat(pipeline.sessionCount()).isEqualTo(1);

        pipeline.openSession("b", init());
        pipeline.openSession("c", init());
        assertThat(pipeline.sessionCount()).isEqualTo(3);
    }

    @Test
    void releaseNonExistentIdIsNoOp() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 3, 50_000);
        pipeline.openSession("a", init());

        pipeline.releaseSession("nonexistent");

        assertThat(pipeline.sessionCount()).isEqualTo(1);
    }

    @Test
    void releaseUnblocksQueuedOpen() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 2, 50_000);
        pipeline.openSession("a", init());
        pipeline.openSession("b", init());

        var opened = new CompletableFuture<ManagedSession>();
        Thread.ofVirtual().start(() ->
                opened.complete(pipeline.openSession("c", init())));

        Thread.sleep(100);
        assertThat(opened.isDone()).isFalse();

        pipeline.releaseSession("a");

        var session = opened.get(5, TimeUnit.SECONDS);
        assertThat(session).isNotNull();
        assertThat(pipeline.sessionCount()).isEqualTo(2);
    }

    @Test
    void sessionCountReflectsOpenAndRelease() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 5, 50_000);

        assertThat(pipeline.sessionCount()).isEqualTo(0);

        pipeline.openSession("a", init());
        assertThat(pipeline.sessionCount()).isEqualTo(1);

        pipeline.openSession("b", init());
        assertThat(pipeline.sessionCount()).isEqualTo(2);

        pipeline.releaseSession("a");
        assertThat(pipeline.sessionCount()).isEqualTo(1);

        pipeline.releaseSession("b");
        assertThat(pipeline.sessionCount()).isEqualTo(0);
    }

    @Test
    void oldConstructorAllowsUnlimitedSessions() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);

        for (int i = 0; i < 15; i++) {
            pipeline.openSession("s" + i, init());
        }
        assertThat(pipeline.sessionCount()).isEqualTo(15);
    }

    @Test
    void shutdownReleasesAllSessionSlots() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 5, 50_000);
        pipeline.openSession("a", init());
        pipeline.openSession("b", init());
        pipeline.openSession("c", init());

        pipeline.shutdown();

        assertThat(pipeline.sessionCount()).isEqualTo(0);
    }

    // --- Infrastructure ---

    private static AgentSessionInit init() {
        return AgentSessionInit.of("test system prompt");
    }


    @Test
    void twoConsumersShareCapacityFairly() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 6, 50_000);

        // Consumer A: characters (3 sticky sessions)
        for (String id : List.of("char:a", "char:b", "char:c")) {
            assertThat(pipeline.openSession(id, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(3);

        // Consumer B: classifier (2 sticky sessions)
        for (String id : List.of("classify:a", "classify:b")) {
            assertThat(pipeline.openSession(id, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(5);

        // Consumer C: consolidation wants 3 but only 1 slot remains
        assertThat(pipeline.openSession("consolidate:0", init())).isNotNull();
        assertThat(pipeline.sessionCount()).isEqualTo(6);

        // Next consolidation session blocks — capacity exhausted
        var blocked = pipeline.openSession("consolidate:1", init(), Duration.ofMillis(100));
        assertThat(blocked).isNull();
    }


    @Test
    void consumerReleasingUnblocksWaitingConsumer() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 4, 50_000);

        // Characters take 3 of 4 slots
        pipeline.openSession("char:a", init());
        pipeline.openSession("char:b", init());
        pipeline.openSession("char:c", init());

        // Classifier takes the last slot
        pipeline.openSession("classify:a", init());
        assertThat(pipeline.sessionCount()).isEqualTo(4);

        // Consolidation tries to open — blocked
        var consolidationOpened = new CompletableFuture<ManagedSession>();
        Thread.ofVirtual().start(() ->
                                         consolidationOpened.complete(pipeline.openSession("consolidate:0", init())));

        Thread.sleep(100);
        assertThat(consolidationOpened.isDone()).isFalse();

        // Classifier finishes and releases
        pipeline.releaseSession("classify:a");

        // Consolidation gets the freed slot
        var session = consolidationOpened.get(5, TimeUnit.SECONDS);
        assertThat(session).isNotNull();
        assertThat(pipeline.sessionCount()).isEqualTo(4); // 3 chars + 1 consolidation
    }


    @Test
    void ticketConcurrencyIndependentOfSessionCount() {
        // 6 sessions allowed, but only 2 concurrent queries
        pipeline = new SessionPipeline(sessionManager, "stub", 2, 6, 50_000);

        pipeline.openSession("a", init());
        pipeline.openSession("b", init());
        pipeline.openSession("c", init());
        pipeline.openSession("d", init());
        assertThat(pipeline.sessionCount()).isEqualTo(4);

        var maxConcurrent     = new java.util.concurrent.atomic.AtomicInteger(0);
        var currentConcurrent = new java.util.concurrent.atomic.AtomicInteger(0);

        var results = pipeline.executeBatch(
                List.of("a", "b", "c", "d"),
                (id, session) -> {
                    int c = currentConcurrent.incrementAndGet();
                    maxConcurrent.updateAndGet(m -> Math.max(m, c));
                    try {Thread.sleep(50);} catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentConcurrent.decrementAndGet();
                    return "ok";
                },
                1, Duration.ofSeconds(5));

        // All 4 succeed but max concurrency bounded by tickets (2), not sessions (4)
        assertThat(results.values().stream().filter(java.util.Objects::nonNull).count()).isEqualTo(4);
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(2);
    }


    @Test
    void consolidationPatternOpenRunRelease() {
        // Backend limit 8: 5 characters + up to 3 consolidation
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 8, 50_000);

        // Characters open 5 sticky sessions
        for (int i = 0; i < 5; i++) {
            pipeline.openSession("char:" + i, init());
        }
        assertThat(pipeline.sessionCount()).isEqualTo(5);

        // Consolidation opens 3 sessions (within remaining capacity)
        for (int i = 0; i < 3; i++) {
            assertThat(pipeline.openSession("cons:" + i, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(8);

        // Consolidation runs batch through its sessions
        var results = pipeline.executeBatch(
                List.of("cons:0", "cons:1", "cons:2"),
                (id, session) -> "consolidated-" + id,
                1, Duration.ofSeconds(5));
        assertThat(results.values().stream().filter(java.util.Objects::nonNull).count()).isEqualTo(3);

        // Consolidation releases its sessions
        for (int i = 0; i < 3; i++) {
            pipeline.releaseSession("cons:" + i);
        }
        assertThat(pipeline.sessionCount()).isEqualTo(5); // only characters remain

        // Character sessions still work
        var charResults = pipeline.executeBatch(
                List.of("char:0", "char:1", "char:2", "char:3", "char:4"),
                (id, session) -> "char-ok-" + id,
                1, Duration.ofSeconds(5));
        assertThat(charResults.values().stream().filter(java.util.Objects::nonNull).count()).isEqualTo(5);
    }


    @Test
    void consolidationBlocksUntilCapacityFreed() throws Exception {
        // Simulate the actual problem: 12-slot backend, 10 held, 6 wanted
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 12, 50_000);

        // Characters hold 5
        for (int i = 0; i < 5; i++) {
            pipeline.openSession("char:" + i, init());
        }
        // Classifier holds 5
        for (int i = 0; i < 5; i++) {
            pipeline.openSession("classify:" + i, init());
        }
        assertThat(pipeline.sessionCount()).isEqualTo(10);

        // Consolidation opens 2 immediately (12 - 10 = 2 available)
        assertThat(pipeline.openSession("cons:0", init())).isNotNull();
        assertThat(pipeline.openSession("cons:1", init())).isNotNull();
        assertThat(pipeline.sessionCount()).isEqualTo(12);

        // Third consolidation session blocks
        var thirdOpened = new CompletableFuture<ManagedSession>();
        Thread.ofVirtual().start(() ->
                                         thirdOpened.complete(pipeline.openSession("cons:2", init())));

        Thread.sleep(100);
        assertThat(thirdOpened.isDone()).isFalse();

        // Classifier releases 1 session — consolidation unblocks
        pipeline.releaseSession("classify:4");
        var session = thirdOpened.get(5, TimeUnit.SECONDS);
        assertThat(session).isNotNull();
        assertThat(pipeline.sessionCount()).isEqualTo(12); // 5 char + 4 classify + 3 cons

        // Consolidation finishes and releases all
        pipeline.releaseSession("cons:0");
        pipeline.releaseSession("cons:1");
        pipeline.releaseSession("cons:2");
        assertThat(pipeline.sessionCount()).isEqualTo(9); // 5 char + 4 classify
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
