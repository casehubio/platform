package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentSharedStateTest {

    private static final int THREADS = 100;
    private static final int OPS_PER_THREAD = 1000;

    @Test
    void counter_concurrentIncrements() throws InterruptedException {
        OrcCounter counter = new DefaultOrcCounter();
        runConcurrently(() -> {
            for (int i = 0; i < OPS_PER_THREAD; i++) counter.increment();
        });
        assertThat(counter.get()).isEqualTo((long) THREADS * OPS_PER_THREAD);
    }

    @Test
    void gauge_concurrentSets_lastWriteWins() throws InterruptedException {
        OrcGauge<Integer> gauge = new DefaultOrcGauge<>();
        runConcurrently(() -> {
            for (int i = 0; i < OPS_PER_THREAD; i++) gauge.set(i);
        });
        assertThat(gauge.get()).isNotNull();
    }

    @Test
    void accumulator_concurrentAccumulates() throws InterruptedException {
        OrcAccumulator acc = new DefaultOrcAccumulator(Double::sum, 0.0);
        runConcurrently(() -> {
            for (int i = 0; i < OPS_PER_THREAD; i++) acc.accumulate(1.0);
        });
        assertThat(acc.get()).isEqualTo((double) THREADS * OPS_PER_THREAD);
    }

    @Test
    void map_concurrentComputeIfAbsent_factoryCalledOnce() throws InterruptedException {
        OrcMap<String, Integer> map = new DefaultOrcMap<>();
        java.util.concurrent.atomic.AtomicInteger factoryCalls = new java.util.concurrent.atomic.AtomicInteger(0);
        runConcurrently(() -> {
            map.computeIfAbsent("key", k -> {
                factoryCalls.incrementAndGet();
                return 42;
            });
        });
        assertThat(map.get("key")).isEqualTo(42);
        assertThat(factoryCalls.get()).isEqualTo(1);
    }

    @Test
    void map_concurrentMerge() throws InterruptedException {
        OrcMap<String, Long> map = new DefaultOrcMap<>();
        map.put("total", 0L);
        runConcurrently(() -> {
            for (int i = 0; i < OPS_PER_THREAD; i++) {
                map.merge("total", 1L, Long::sum);
            }
        });
        assertThat(map.get("total")).isEqualTo((long) THREADS * OPS_PER_THREAD);
    }

    private void runConcurrently(Runnable task) throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < THREADS; t++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        task.run();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            startGate.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
