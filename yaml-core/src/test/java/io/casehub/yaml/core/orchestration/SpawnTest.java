package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SpawnTest {

    @Test
    void spawn_startsVirtualThread_isDoneOnCompletion() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var completed = new AtomicBoolean(false);

        SpawnedTask task = scope.spawn("worker", () -> completed.set(true));

        task.join(Duration.ofSeconds(1));
        assertThat(task.isDone()).isTrue();
        assertThat(task.isFailed()).isFalse();
        assertThat(completed.get()).isTrue();
        scope.close();
    }

    @Test
    void spawn_failedTask_reportsException() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var error = new RuntimeException("boom");

        SpawnedTask task = scope.spawn("failing", () -> { throw error; });

        task.join(Duration.ofSeconds(1));
        assertThat(task.isDone()).isTrue();
        assertThat(task.isFailed()).isTrue();
        assertThat(task.exception()).isSameAs(error);
        scope.close();
    }

    @Test
    void spawn_join_blocksUntilCompletion() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var started = new CountDownLatch(1);

        SpawnedTask task = scope.spawn("slow", () -> {
            started.countDown();
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        started.await(1, TimeUnit.SECONDS);
        task.join();
        assertThat(task.isDone()).isTrue();
        scope.close();
    }

    @Test
    void spawn_joinWithTimeout_returnsFalseOnTimeout() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var blocker = new CountDownLatch(1);

        SpawnedTask task = scope.spawn("blocked", () -> {
            try { blocker.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        boolean joined = task.join(Duration.ofMillis(50));
        assertThat(joined).isFalse();
        scope.close();
    }

    @Test
    void close_interruptsSpawnedTasks() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var interrupted = new AtomicBoolean(false);
        var started = new CountDownLatch(1);

        scope.spawn("waiting", () -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });

        started.await(1, TimeUnit.SECONDS);
        scope.close();
        Thread.sleep(100);
        assertThat(interrupted.get()).isTrue();
    }

    @Test
    void close_interruptsChannelReceive() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var interrupted = new AtomicBoolean(false);
        var started = new CountDownLatch(1);
        OrcChannel<String> ch = scope.channel("data");

        scope.spawn("receiver", () -> {
            started.countDown();
            try {
                ch.receive();
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
        });

        started.await(1, TimeUnit.SECONDS);
        Thread.sleep(50);
        scope.close();
        Thread.sleep(100);
        assertThat(interrupted.get()).isTrue();
    }

    @Test
    void spawn_name_isAccessible() {
        var scope = new DefaultScenarioScope();
        SpawnedTask task = scope.spawn("my-worker", () -> {});
        assertThat(task.name()).isEqualTo("my-worker");
        scope.close();
    }

    @Test
    void multipleSpawns_allInterruptedOnClose() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var count = new java.util.concurrent.atomic.AtomicInteger(0);
        var allStarted = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            scope.spawn("worker-" + i, () -> {
                allStarted.countDown();
                try { Thread.sleep(60_000); } catch (InterruptedException e) { count.incrementAndGet(); }
            });
        }

        allStarted.await(1, TimeUnit.SECONDS);
        scope.close();
        Thread.sleep(200);
        assertThat(count.get()).isEqualTo(3);
    }
}
