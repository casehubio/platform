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
