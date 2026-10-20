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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPipelineMultiTickTest {

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
    void multiTickWithPostTickWorkBetweenBatches() {
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 50_000);
        openSessions("a", "b", "c", "d", "e");

        int ticks = 10;
        var totalQueries = new AtomicInteger();

        for (int tick = 0; tick < ticks; tick++) {
            var results = pipeline.executeBatch(
                    List.of("a", "b", "c", "d", "e"),
                    (id, session) -> {
                        session.query("prompt").subscribe().asStream().count();
                        totalQueries.incrementAndGet();
                        return "ok-" + id;
                    },
                    1, Duration.ofSeconds(5));

            assertThat(results.values().stream().filter(java.util.Objects::nonNull).count())
                    .as("Tick %d: all 5 should succeed", tick)
                    .isEqualTo(5);

            // Simulate post-tick work: direct session queries OUTSIDE the batch
            // (like ExchangeRunner or classifier would do)
            for (String id : List.of("a", "b")) {
                var session = pipeline.sessionFor(id);
                session.query("post-tick-query").subscribe().asStream().count();
            }
        }

        assertThat(totalQueries.get()).isEqualTo(50);
        assertThat(pipeline.availablePermits()).isEqualTo(5);
    }

    @Test
    void batchCompletesEvenWhenSessionsQueriedDirectlyBetween() {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        openSessions("a", "b", "c");

        // Batch 1
        pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> {
                    session.query("batch1").subscribe().asStream().count();
                    return "ok";
                },
                1, Duration.ofSeconds(5));

        // Direct session usage between batches (simulates exchange/classifier calls)
        pipeline.sessionFor("a").query("direct-between").subscribe().asStream().count();
        pipeline.sessionFor("b").query("direct-between").subscribe().asStream().count();

        // Batch 2 — must still complete without deadlock
        var results = pipeline.executeBatch(
                List.of("a", "b", "c"),
                (id, session) -> {
                    session.query("batch2").subscribe().asStream().count();
                    return "ok2";
                },
                1, Duration.ofSeconds(5));

        assertThat(results.get("a")).isEqualTo("ok2");
        assertThat(results.get("b")).isEqualTo("ok2");
        assertThat(results.get("c")).isEqualTo("ok2");
        assertThat(pipeline.availablePermits()).isEqualTo(3);
    }

    @Test
    void permitsNotLeakedAcrossManyTicks() {
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 50_000);
        openSessions("a", "b", "c", "d", "e");

        for (int tick = 0; tick < 50; tick++) {
            pipeline.executeBatch(
                    List.of("a", "b", "c", "d", "e"),
                    (id, session) -> {
                        session.query("t").subscribe().asStream().count();
                        return "ok";
                    },
                    1, Duration.ofSeconds(5));

            assertThat(pipeline.availablePermits())
                    .as("Tick %d: permits must be fully restored", tick)
                    .isEqualTo(5);
        }

        // 50 ticks × 5 queries = 250 total
        assertThat(pipeline.sessionFor("a").metrics().totalQueries()).isEqualTo(250);
    }

    @Test
    void concurrentBatchAndDirectSessionAccessDoesNotDeadlock() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 3, 50_000);
        openSessions("a", "b", "c");

        // Run batch on one thread while another thread queries sessions directly
        var batchDone = new java.util.concurrent.CountDownLatch(1);
        var directDone = new java.util.concurrent.CountDownLatch(1);

        Thread.ofVirtual().start(() -> {
            pipeline.executeBatch(
                    List.of("a", "b", "c"),
                    (id, session) -> {
                        try { Thread.sleep(50); } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        session.query("batch").subscribe().asStream().count();
                        return "ok";
                    },
                    1, Duration.ofSeconds(5));
            batchDone.countDown();
        });

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(10); // start slightly after batch
                // Direct queries don't go through the pipeline's tickets
                pipeline.sessionFor("a").query("direct").subscribe().asStream().count();
            } catch (Exception e) {
                // session might be busy — that's OK
            }
            directDone.countDown();
        });

        assertThat(batchDone.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("Batch must complete without deadlock")
                .isTrue();
        assertThat(directDone.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("Direct access must complete")
                .isTrue();
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
