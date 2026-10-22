package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import io.casehub.platform.agent.gate.AimdAdmissionStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConsolidationDeadlockTest {

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
    void sameThreadDeadlocksWithSharedAdmission() throws Exception {
        var admission = new AimdAdmissionStrategy(12, 5, Duration.ofSeconds(10));

        // Collapse window via rollbacks: 12→6→3→1
        admission.tryAcquire(Duration.ofMillis(100));
        admission.rollback();
        assertThat(admission.currentWindow()).isEqualTo(6);

        admission.tryAcquire(Duration.ofMillis(100));
        admission.rollback();
        assertThat(admission.currentWindow()).isEqualTo(3);

        admission.tryAcquire(Duration.ofMillis(100));
        admission.rollback();
        assertThat(admission.currentWindow()).isEqualTo(1);

        // Scenario acquires the single available permit
        assertThat(admission.tryAcquire(Duration.ofMillis(100))).isTrue();
        assertThat(admission.availablePermits()).isEqualTo(0);

        // Same thread tries consolidation — available=0, blocks until timeout
        assertThat(admission.tryAcquirePriority(Duration.ofMillis(200)))
                .as("Same-thread consolidation must timeout — this IS the deadlock")
                .isFalse();

        admission.release();
    }

    @Test
    void companionAdmissionPreventsDeadlock() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 12, 50_000);
        openSessions("scenario:0");

        // Collapse main admission: 12→6→3→1
        var main = pipeline.admission();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        assertThat(main.currentWindow()).isEqualTo(1);

        // Scenario acquires the single permit — main admission is saturated
        assertThat(main.tryAcquire(Duration.ofMillis(100))).isTrue();

        // Consolidation uses companion admission — independent window, no contention
        var consAdmission = pipeline.companionAdmission("consolidation", 6);
        assertThat(consAdmission.tryAcquirePriority(Duration.ofMillis(100)))
                .as("Companion admission must succeed — independent of main")
                .isTrue();
        consAdmission.release();

        main.release();
    }

    @Test
    void companionWindowIsIndependentOfMain() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 12, 50_000);

        var main = pipeline.admission();
        var companion = pipeline.companionAdmission("consolidation", 6);

        // Collapse main admission: 12→1
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();

        // Main window collapsed, companion untouched
        assertThat(main.currentWindow()).isEqualTo(1);
        assertThat(companion.currentWindow()).isEqualTo(6);
        assertThat(companion.ceiling()).isEqualTo(6);

        // Rollback companion — main unaffected
        companion.tryAcquire(Duration.ofMillis(100));
        companion.rollback();
        assertThat(companion.currentWindow()).isEqualTo(3);
        assertThat(main.currentWindow()).isEqualTo(1);
    }

    @Test
    void companionAdmissionIsStableAcrossCalls() {
        pipeline = new SessionPipeline(sessionManager, "stub", 12, 50_000);

        var first = pipeline.companionAdmission("consolidation", 6);
        var second = pipeline.companionAdmission("consolidation", 6);

        assertThat(second).isSameAs(first);
    }

    @Test
    void multipleCompanionsAreIndependent() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 12, 50_000);

        var consA = pipeline.companionAdmission("consolidation", 6);
        var consB = pipeline.companionAdmission("classification", 4);

        assertThat(consA).isNotSameAs(consB);
        assertThat(consA.ceiling()).isEqualTo(6);
        assertThat(consB.ceiling()).isEqualTo(4);

        // Collapse A, B unaffected
        consA.tryAcquire(Duration.ofMillis(100));
        consA.rollback();
        consA.tryAcquire(Duration.ofMillis(100));
        consA.rollback();
        assertThat(consA.currentWindow()).isEqualTo(1);
        assertThat(consB.currentWindow()).isEqualTo(4);
    }

    @Test
    void endToEndConsolidationWithCollapsedScenarioWindow() throws Exception {
        pipeline = new SessionPipeline(sessionManager, "stub", 12, 50_000);
        openSessions("char:0", "char:1", "char:2");
        openSessions("cons:0", "cons:1", "cons:2");

        // Simulate 429 errors collapsing the scenario window
        var main = pipeline.admission();
        for (int i = 0; i < 4; i++) {
            main.tryAcquire(Duration.ofMillis(100));
            main.rollback();
        }
        assertThat(main.currentWindow()).isLessThanOrEqualTo(1);

        // Scenario tick acquires its one permit
        assertThat(main.tryAcquire(Duration.ofMillis(100))).isTrue();

        // Consolidation runs batch through companion admission — must not deadlock
        var consAdmission = pipeline.companionAdmission("consolidation", 6);

        var completed = new AtomicInteger(0);
        var deadlocked = new AtomicBoolean(false);

        // Simulate consolidation phases running 3 concurrent LLM calls
        var threads = new Thread[3];
        for (int i = 0; i < 3; i++) {
            int idx = i;
            threads[i] = Thread.ofVirtual().name("cons-" + i).start(() -> {
                try {
                    if (!consAdmission.tryAcquirePriority(Duration.ofMillis(500))) {
                        deadlocked.set(true);
                        return;
                    }
                    Thread.sleep(10); // simulate LLM call
                    consAdmission.release();
                    completed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        for (var t : threads) t.join(2000);

        assertThat(deadlocked.get())
                .as("Consolidation must not deadlock — companion admission is independent")
                .isFalse();
        assertThat(completed.get()).isEqualTo(3);

        // Scenario resumes — release its permit
        main.release();
        assertThat(main.availablePermits()).isGreaterThanOrEqualTo(1);
    }

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
