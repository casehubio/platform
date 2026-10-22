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

import static org.assertj.core.api.Assertions.assertThat;

class PipelineSessionReaperTest {

    private SessionPoolRegistry poolRegistry;
    private SessionLifecycleManager sessionManager;
    private SessionPipeline pipeline;

    @BeforeEach
    void setUp() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                0, 10, null, Map.of(), Map.of());
        poolRegistry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());
        sessionManager = new SessionLifecycleManager(poolRegistry);
        pipeline = new SessionPipeline(sessionManager, "stub", 5, 50_000);
    }

    @AfterEach
    void tearDown() {
        pipeline.shutdown();
        sessionManager.shutdown();
        poolRegistry.shutdown();
    }

    @Test
    void sweepEvictsSessionsIdleLongerThanThreshold() throws Exception {
        pipeline.openSession("a", AgentSessionInit.of("prompt"));
        pipeline.openSession("b", AgentSessionInit.of("prompt"));
        assertThat(pipeline.sessionCount()).isEqualTo(2);

        Thread.sleep(150);

        pipeline.evictIdleSessions(Duration.ofMillis(100));
        assertThat(pipeline.sessionCount()).isEqualTo(0);
    }

    @Test
    void sweepKeepsRecentlyUsedSessions() throws Exception {
        pipeline.openSession("a", AgentSessionInit.of("prompt"));
        var originalA = pipeline.sessionFor("a");
        Thread.sleep(150);
        pipeline.openSession("b", AgentSessionInit.of("prompt"));

        pipeline.evictIdleSessions(Duration.ofMillis(100));
        assertThat(pipeline.sessionCount()).isEqualTo(1);
        assertThat(pipeline.sessionFor("b")).isNotNull();
        // "a" was evicted from the session map but transparently recreates on access
        var recreatedA = pipeline.sessionFor("a");
        assertThat(recreatedA).isNotNull();
        assertThat(recreatedA).isNotSameAs(originalA);
    }

    @Test
    void sweepWithLargeThresholdEvictsNothing() {
        pipeline.openSession("a", AgentSessionInit.of("prompt"));
        pipeline.openSession("b", AgentSessionInit.of("prompt"));

        pipeline.evictIdleSessions(Duration.ofHours(1));
        assertThat(pipeline.sessionCount()).isEqualTo(2);
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
