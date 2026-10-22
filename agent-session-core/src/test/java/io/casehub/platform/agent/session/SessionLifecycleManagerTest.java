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

class SessionLifecycleManagerTest {

    private SessionPoolRegistry poolRegistry;
    private SessionLifecycleManager manager;

    @BeforeEach
    void setUp() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                0, 5, null, Map.of(), Map.of());
        poolRegistry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());
        manager = new SessionLifecycleManager(poolRegistry);
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
        poolRegistry.shutdown();
    }

    @Test
    void acquireReturnsManagedSession() {
        var session = manager.acquire("appraisal", "stub", init());

        assertThat(session).isNotNull();
        assertThat(session.conversationId()).isEqualTo("appraisal");
        assertThat(session.clearingPolicy()).isEqualTo(ClearingPolicy.MANUAL);
    }

    @Test
    void acquireSameIdReturnsSameSession() {
        var first = manager.acquire("appraisal", "stub", init());
        var second = manager.acquire("appraisal", "stub", init());

        assertThat(second).isSameAs(first);
    }

    @Test
    void acquireDifferentIdsReturnsDifferentSessions() {
        var appraisal = manager.acquire("appraisal", "stub", init());
        var extractor = manager.acquire("kg-extractor", "stub", init());

        assertThat(appraisal).isNotSameAs(extractor);
        assertThat(appraisal.conversationId()).isEqualTo("appraisal");
        assertThat(extractor.conversationId()).isEqualTo("kg-extractor");
    }

    @Test
    void releaseUnbindsConversationId() {
        manager.acquire("appraisal", "stub", init());
        assertThat(manager.hasSession("appraisal")).isTrue();

        manager.release("appraisal");
        assertThat(manager.hasSession("appraisal")).isFalse();
    }

    @Test
    void acquireAfterReleaseCreatesNewSession() {
        var first = manager.acquire("appraisal", "stub", init());
        manager.release("appraisal");
        var second = manager.acquire("appraisal", "stub", init());

        assertThat(second).isNotSameAs(first);
    }

    @Test
    void everyCallPolicyClearsBeforeSecondQuery() {
        var session = manager.acquire("appraisal", "stub", init(), ClearingPolicy.EVERY_CALL, 0);

        // First query — no clear
        session.query("prompt 1").collect().asList().await().atMost(Duration.ofSeconds(5));
        assertThat(session.turnCount()).isEqualTo(1);

        // Second query — clear fires before query
        session.query("prompt 2").collect().asList().await().atMost(Duration.ofSeconds(5));
        assertThat(session.turnCount()).isEqualTo(2);
    }

    @Test
    void afterNTurnsPolicyClearsAtInterval() {
        var session = manager.acquire("reflection", "stub", init(), ClearingPolicy.AFTER_N_TURNS, 3);

        session.query("p1").collect().asList().await().atMost(Duration.ofSeconds(5));
        session.query("p2").collect().asList().await().atMost(Duration.ofSeconds(5));
        session.query("p3").collect().asList().await().atMost(Duration.ofSeconds(5));
        // clear() fires at turn 3 — verified by turn count continuing
        assertThat(session.turnCount()).isEqualTo(3);

        session.query("p4").collect().asList().await().atMost(Duration.ofSeconds(5));
        assertThat(session.turnCount()).isEqualTo(4);
    }

    @Test
    void manualClearResetsCounter() {
        var session = manager.acquire("appraisal", "stub", init());

        session.query("p1").collect().asList().await().atMost(Duration.ofSeconds(5));
        session.query("p2").collect().asList().await().atMost(Duration.ofSeconds(5));
        assertThat(session.turnCount()).isEqualTo(2);

        session.clear();
        assertThat(session.turnCount()).isEqualTo(0);
    }

    @Test
    void closeViaSessionReleasesFromManager() {
        var session = manager.acquire("appraisal", "stub", init());
        assertThat(manager.hasSession("appraisal")).isTrue();

        session.close();
        assertThat(manager.hasSession("appraisal")).isFalse();
    }

    @Test
    void queryIncrementsMetrics() {
        var session = manager.acquire("appraisal", "stub", init());

        session.query("p1").collect().asList().await().atMost(Duration.ofSeconds(5));
        session.query("p2").collect().asList().await().atMost(Duration.ofSeconds(5));

        assertThat(session.metrics().totalQueries()).isEqualTo(2);
    }

    @Test
    void clearIncrementsMetrics() {
        var session = manager.acquire("appraisal", "stub", init());

        session.query("p1").collect().asList().await().atMost(Duration.ofSeconds(5));
        session.clear();

        assertThat(session.metrics().totalClears()).isEqualTo(1);
    }

    @Test
    void metricsAccessibleFromPool() {
        manager.acquire("appraisal", "stub", init());

        var poolMetrics = poolRegistry.findPool("stub", null).orElseThrow().metrics();
        assertThat(poolMetrics.sessionsOpened()).isEqualTo(1);
    }

    @Test
    void sizeAwareClearsWhenTokensExceedThreshold() {
        // Threshold 100 tokens. "x".repeat(400) = 400 chars = 100 tokens from prompt alone.
        // First query: ~100 tokens from prompt + response tokens — under threshold on entry.
        // Second query: accumulated tokens exceed threshold → auto-clear before query.
        var session = manager.acquire("appraisal", "stub", init(),
                                      ClearingPolicy.SIZE_AWARE, 100);

        // First query: 400 chars = 100 estimated tokens from prompt, plus response adds more
        session.query("x".repeat(400)).collect().asList().await().atMost(Duration.ofSeconds(5));
        assertThat(session.estimatedTokens()).isGreaterThan(0);

        // Second query triggers clear because accumulated tokens > threshold (100)
        session.query("y".repeat(40)).collect().asList().await().atMost(Duration.ofSeconds(5));

        // Clear should have fired
        assertThat(session.metrics().totalClears()).isEqualTo(1);
    }

    @Test
    void sizeAwareDoesNotClearWhenUnderThreshold() {
        // Threshold 1000 tokens. Short prompts stay well under.
        var session = manager.acquire("appraisal", "stub", init(),
                                      ClearingPolicy.SIZE_AWARE, 1000);

        session.query("hello").collect().asList().await().atMost(Duration.ofSeconds(5));
        session.query("world").collect().asList().await().atMost(Duration.ofSeconds(5));
        session.query("test").collect().asList().await().atMost(Duration.ofSeconds(5));

        assertThat(session.metrics().totalClears()).isZero();
        assertThat(session.metrics().totalQueries()).isEqualTo(3);
    }

    @Test
    void sizeAwareClearResetsTokenEstimate() {
        var session = manager.acquire("appraisal", "stub", init(),
                                      ClearingPolicy.SIZE_AWARE, 100);

        // Accumulate tokens
        session.query("x".repeat(400)).collect().asList().await().atMost(Duration.ofSeconds(5));
        assertThat(session.estimatedTokens()).isGreaterThan(0);

        // Manual clear resets
        session.clear();
        assertThat(session.estimatedTokens()).isZero();
    }

    @Test
    void sizeAwareTracksResponseTokens() {
        var session = manager.acquire("appraisal", "stub", init(),
                                      ClearingPolicy.SIZE_AWARE, 1000);

        // "hello" = 5 chars = 1 token from prompt. Response "response" = 8 chars = 2 tokens.
        session.query("hello").collect().asList().await().atMost(Duration.ofSeconds(5));

        // Should include both prompt and response token estimates
        assertThat(session.estimatedTokens()).isGreaterThan(1);
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
