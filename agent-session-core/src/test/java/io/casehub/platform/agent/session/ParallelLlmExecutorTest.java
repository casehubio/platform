package io.casehub.platform.agent.session;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.config.PoolDeclaration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class ParallelLlmExecutorTest {

    private SessionPoolRegistry poolRegistry;
    private SessionLifecycleManager sessionManager;
    private SessionPipeline pipeline;

    @BeforeEach
    void setUp() {
        var decl = new PoolDeclaration("test-pool", "test", "stub",
                0, 20, null, Map.of(), Map.of());
        poolRegistry = new SessionPoolRegistry(List.of(decl), stubBackendRegistry());
        sessionManager = new SessionLifecycleManager(poolRegistry);
        pipeline = new SessionPipeline(sessionManager, "stub", 12, 50_000);
    }

    @AfterEach
    void tearDown() {
        if (pipeline != null) pipeline.shutdown();
        sessionManager.shutdown();
        poolRegistry.shutdown();
    }

    @Test
    void allTasksComplete() {
        var results = new CopyOnWriteArrayList<String>();
        List<Consumer<AgentSession>> tasks = List.of(
                s -> results.add("a"),
                s -> results.add("b"),
                s -> results.add("c")
        );

        var result = ParallelLlmExecutor.execute(
                pipeline, "test", 6, "system", "phase1", tasks);

        assertThat(result.tasksSubmitted()).isEqualTo(3);
        assertThat(result.tasksCompleted()).isEqualTo(3);
        assertThat(result.wallClockMs()).isGreaterThanOrEqualTo(0);
        assertThat(results).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void emptyTaskListReturnsImmediately() {
        var result = ParallelLlmExecutor.execute(
                pipeline, "test", 6, "system", "empty", List.of());

        assertThat(result.tasksSubmitted()).isEqualTo(0);
        assertThat(result.tasksCompleted()).isEqualTo(0);
        assertThat(result.wallClockMs()).isEqualTo(0);
    }

    @Test
    void failedTasksDoNotLeakPermits() {
        var completed = new AtomicInteger(0);
        List<Consumer<AgentSession>> tasks = List.of(
                s -> { completed.incrementAndGet(); },
                s -> { throw new RuntimeException("parse error"); },
                s -> { completed.incrementAndGet(); }
        );

        var result = ParallelLlmExecutor.execute(
                pipeline, "test", 6, "system", "leak-test", tasks);

        assertThat(result.tasksCompleted()).isEqualTo(2);

        var admission = pipeline.companionAdmission("test", 6);
        assertThat(admission.activeCount()).isEqualTo(0);
        assertThat(admission.availablePermits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void capacityErrorTriggersRollback() {
        List<Consumer<AgentSession>> tasks = List.of(
                s -> { throw new SessionCapacityException("429 rate limit"); },
                s -> { /* success */ }
        );

        ParallelLlmExecutor.execute(pipeline, "rollback-test", 6, "system", "cap-test", tasks);

        var admission = pipeline.companionAdmission("rollback-test", 6);
        assertThat(admission.currentWindow())
                .as("Capacity error should halve AIMD window")
                .isLessThan(6);
        assertThat(admission.activeCount()).isEqualTo(0);
    }

    @Test
    void usesCompanionAdmissionNotMainAdmission() {
        var mainBefore = pipeline.admission().currentWindow();

        List<Consumer<AgentSession>> tasks = List.of(
                s -> { throw new SessionCapacityException("429"); }
        );
        ParallelLlmExecutor.execute(pipeline, "isolated", 4, "system", "isolation-test", tasks);

        assertThat(pipeline.admission().currentWindow())
                .as("Main admission window must be unaffected by executor rollbacks")
                .isEqualTo(mainBefore);

        var companion = pipeline.companionAdmission("isolated", 4);
        assertThat(companion.currentWindow())
                .as("Companion admission should have been rolled back")
                .isLessThan(4);
    }

    @Test
    void executorDoesNotDeadlockWithCollapsedMainWindow() throws Exception {
        var main = pipeline.admission();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        main.tryAcquire(Duration.ofMillis(100));
        main.rollback();
        assertThat(main.currentWindow()).isEqualTo(1);

        assertThat(main.tryAcquire(Duration.ofMillis(100))).isTrue();

        var completed = new AtomicBoolean(false);
        List<Consumer<AgentSession>> tasks = List.of(
                s -> completed.set(true)
        );

        var result = ParallelLlmExecutor.execute(
                pipeline, "consolidation", 6, "system", "no-deadlock", tasks);

        assertThat(result.tasksCompleted()).isEqualTo(1);
        assertThat(completed.get())
                .as("Task must complete — companion admission independent of collapsed main")
                .isTrue();

        main.release();
    }

    @Test
    void concurrencyBoundedByPoolSize() {
        var maxConcurrent = new AtomicInteger(0);
        var currentConcurrent = new AtomicInteger(0);

        List<Consumer<AgentSession>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(s -> {
                int c = currentConcurrent.incrementAndGet();
                maxConcurrent.updateAndGet(m -> Math.max(m, c));
                try { Thread.sleep(20); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                currentConcurrent.decrementAndGet();
            });
        }

        var result = ParallelLlmExecutor.execute(
                pipeline, "bounded", 3, "system", "concurrency-test", 3, tasks);

        assertThat(result.tasksCompleted()).isEqualTo(10);
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(3);
    }

    @Test
    void sessionsReleasedAfterExecution() {
        int before = pipeline.sessionCount();

        ParallelLlmExecutor.execute(
                pipeline, "cleanup", 6, "system", "release-test",
                List.of(s -> { /* no-op */ }, s -> { /* no-op */ }));

        assertThat(pipeline.sessionCount())
                .as("Executor sessions must be released after execution")
                .isEqualTo(before);
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
