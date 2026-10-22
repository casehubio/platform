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

    @Test
    void multiTickWithSlowSessionsDoesNotStall() {
        var slowBackend = new SlowBackend(200);
        var decl = new PoolDeclaration("slow-pool", "test", "slow",
                                       0, 10, null, Map.of(), Map.of());
        var slowRegistry       = new SessionPoolRegistry(List.of(decl), slowBackendRegistry(slowBackend));
        var slowSessionManager = new SessionLifecycleManager(slowRegistry);
        pipeline = new SessionPipeline(slowSessionManager, "slow", 5, 50_000);

        for (String id : List.of("a", "b", "c", "d", "e")) {
            pipeline.openSession(id, AgentSessionInit.of("system prompt"));
        }

        int  ticks = 5;
        long start = System.currentTimeMillis();

        for (int tick = 0; tick < ticks; tick++) {
            var results = pipeline.executeBatch(
                    List.of("a", "b", "c", "d", "e"),
                    (id, session) -> {
                        session.query("prompt " + id).subscribe().asStream().count();
                        return "ok-" + id;
                    },
                    1, Duration.ofSeconds(10));

            long succeeded = results.values().stream()
                                    .filter(java.util.Objects::nonNull).count();
            assertThat(succeeded)
                    .as("Tick %d: all 5 should succeed with slow sessions", tick)
                    .isEqualTo(5);
        }

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).as("5 ticks with 200ms sessions should complete in <5s")
                           .isLessThan(5000);
        assertThat(pipeline.availablePermits()).isEqualTo(5);

        slowSessionManager.shutdown();
        slowRegistry.shutdown();
    }

    @Test
    void multiTickWithObservationPhaseAndBackgroundWorkDoesNotStall() throws Exception {
        var slowBackend = new SlowBackend(200);
        var decl = new PoolDeclaration("slow-pool", "test", "slow",
                                       0, 10, null, Map.of(), Map.of());
        var slowRegistry       = new SessionPoolRegistry(List.of(decl), slowBackendRegistry(slowBackend));
        var slowSessionManager = new SessionLifecycleManager(slowRegistry);
        pipeline = new SessionPipeline(slowSessionManager, "slow", 5, 50_000);

        var ids = List.of("a", "b", "c", "d", "e");
        for (String id : ids) {
            pipeline.openSession(id, AgentSessionInit.of("system prompt"));
        }

        int ticks = 5;
        for (int tick = 0; tick < ticks; tick++) {
            // Phase 1: observation building in parallel virtual threads (like orchestrator)
            var prompts  = new java.util.concurrent.ConcurrentHashMap<String, String>();
            var obsLatch = new java.util.concurrent.CountDownLatch(ids.size());
            for (String id : ids) {
                Thread.ofVirtual().name(id + "-obs-" + tick).start(() -> {
                    try {
                        Thread.sleep(10); // simulate observation building
                        prompts.put(id, "prompt for " + id);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        obsLatch.countDown();
                    }
                });
            }
            assertThat(obsLatch.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    .as("Tick %d: observation phase must complete", tick)
                    .isTrue();

            // Phase 2: pipeline batch with slow sessions
            var results = pipeline.executeBatch(
                    prompts.keySet(),
                    (id, session) -> {
                        session.query(prompts.get(id)).subscribe().asStream().count();
                        return "ok-" + id;
                    },
                    1, Duration.ofSeconds(5));

            assertThat(results.values().stream().filter(java.util.Objects::nonNull).count())
                    .as("Tick %d: all 5 should succeed", tick)
                    .isEqualTo(5);

            // Background work between ticks (like narrator)
            Thread.ofVirtual().name("narrator-" + tick).start(() -> {
                try {Thread.sleep(300);} catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertThat(pipeline.availablePermits()).isEqualTo(5);

        slowSessionManager.shutdown();
        slowRegistry.shutdown();
    }


    static class SlowBackend implements io.casehub.platform.agent.AgentBackend {
        private final long delayMs;

        SlowBackend(long delayMs) {this.delayMs = delayMs;}

        @Override
        public String key()       {return "slow";}

        @Override
        public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> invoke(
                io.casehub.platform.agent.AgentSessionConfig config) {
            return io.smallrye.mutiny.Multi.createFrom().item(
                    new io.casehub.platform.agent.AgentEvent.TextDelta("response"));
        }

        @Override
        public io.casehub.platform.agent.AgentSession openSession(
                io.casehub.platform.agent.AgentSessionInit init) {
            return new SlowSession(delayMs);
        }
    }

    static class SlowSession implements io.casehub.platform.agent.AgentSession {
        private final long delayMs;

        SlowSession(long delayMs) {this.delayMs = delayMs;}

        @Override
        public io.smallrye.mutiny.Multi<io.casehub.platform.agent.AgentEvent> query(String prompt) {
            try {Thread.sleep(delayMs);} catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return io.smallrye.mutiny.Multi.createFrom().item(
                    (io.casehub.platform.agent.AgentEvent) new io.casehub.platform.agent.AgentEvent.TextDelta("slow-response"));
        }

        @Override
        public io.smallrye.mutiny.Uni<Void> interrupt() {
            return io.smallrye.mutiny.Uni.createFrom().voidItem();
        }

        @Override
        public void clear()                 {}

        @Override
        public void close(Duration timeout) {}
    }

    static io.casehub.platform.agent.BackendInstanceRegistry slowBackendRegistry(SlowBackend backend) {
        return new io.casehub.platform.agent.BackendInstanceRegistry() {
            @Override
            public void register(io.casehub.platform.agent.AgentBackend b) {}

            @Override
            public java.util.Optional<io.casehub.platform.agent.AgentBackend> resolve(String key, String instanceId) {
                return "slow".equals(key) ? java.util.Optional.of(backend) : java.util.Optional.empty();
            }

            @Override
            public java.util.List<io.casehub.platform.agent.AgentBackend> resolveByKey(String key) {
                return "slow".equals(key) ? java.util.List.of(backend) : java.util.List.of();
            }
        };
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
