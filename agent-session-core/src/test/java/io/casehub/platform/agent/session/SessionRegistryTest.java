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
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);

        for (String id : List.of("a", "b", "c")) {
            assertThat(pipeline.openSession(id, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(3);
    }

    @Test
    void sessionsAreUnlimited() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);

        for (int i = 0; i < 20; i++) {
            assertThat(pipeline.openSession("s" + i, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(20);
    }

    @Test
    void releaseSessionReducesCount() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        pipeline.openSession("a", init());
        pipeline.openSession("b", init());
        assertThat(pipeline.sessionCount()).isEqualTo(2);

        pipeline.releaseSession("a");
        assertThat(pipeline.sessionCount()).isEqualTo(1);

        pipeline.releaseSession("b");
        assertThat(pipeline.sessionCount()).isEqualTo(0);
    }

    @Test
    void duplicateOpenReturnsSameSessionWithoutConsumingSlot() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);

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
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        pipeline.openSession("a", init());

        pipeline.releaseSession("nonexistent");

        assertThat(pipeline.sessionCount()).isEqualTo(1);
    }

    @Test
    void openSessionNeverBlocks() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        for (int i = 0; i < 15; i++) {
            assertThat(pipeline.openSession("s" + i, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(15);
    }

    @Test
    void sessionCountReflectsOpenAndRelease() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);

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
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
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
    void multipleConsumersCanOpenSessionsFreely() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 50_000);

        for (String id : List.of("char:a", "char:b", "char:c")) {
            assertThat(pipeline.openSession(id, init())).isNotNull();
        }
        for (String id : List.of("classify:a", "classify:b")) {
            assertThat(pipeline.openSession(id, init())).isNotNull();
        }
        for (int i = 0; i < 5; i++) {
            assertThat(pipeline.openSession("consolidate:" + i, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(10);
    }


    @Test
    void releaseAndReopenWorks() {
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 50_000);
        pipeline.openSession("a", init());
        assertThat(pipeline.sessionCount()).isEqualTo(1);

        pipeline.releaseSession("a");
        assertThat(pipeline.sessionCount()).isEqualTo(0);

        assertThat(pipeline.openSession("a", init())).isNotNull();
        assertThat(pipeline.sessionCount()).isEqualTo(1);
    }


    @Test
    void ticketConcurrencyIndependentOfSessionCount() {
        // 6 sessions allowed, but only 2 concurrent queries
        pipeline = new SessionPipeline(sessionManager, "stub", 2, 50_000);

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
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 50_000);

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
    void consolidationSessionsOpenWithoutBlocking() {
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 50_000);

        for (int i = 0; i < 5; i++) {
            pipeline.openSession("char:" + i, init());
        }
        for (int i = 0; i < 5; i++) {
            pipeline.openSession("classify:" + i, init());
        }
        assertThat(pipeline.sessionCount()).isEqualTo(10);

        for (int i = 0; i < 6; i++) {
            assertThat(pipeline.openSession("cons:" + i, init())).isNotNull();
        }
        assertThat(pipeline.sessionCount()).isEqualTo(16);

        for (int i = 0; i < 6; i++) {
            pipeline.releaseSession("cons:" + i);
        }
        assertThat(pipeline.sessionCount()).isEqualTo(10);
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
