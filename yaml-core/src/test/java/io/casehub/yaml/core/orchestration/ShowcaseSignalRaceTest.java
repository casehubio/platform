package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Showcase: signal-based step coordination and race pattern.
 *
 * Equivalent YAML (signal/wait):
 * <pre>
 * - step: producer
 *   action: generate-data
 *   signal: data-ready
 *
 * - step: consumer
 *   wait: data-ready
 *   action: process-data
 *   data: { input: ${signal.data-ready.payload} }
 * </pre>
 *
 * Equivalent YAML (race):
 * <pre>
 * - step: race-feeds
 *   race: [primary-feed, backup-feed]
 *   timeout: 5s
 * </pre>
 */
class ShowcaseSignalRaceTest {

    @Test
    void signalWait_producerConsumerCoordination() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var signal = scope.signal("data-ready");
        var results = scope.resultStore();
        var consumerResult = new AtomicReference<>();

        // Producer generates data, then signals
        Thread.ofVirtual().name("producer").start(() -> {
            Map<String, Object> data = Map.of("prices", java.util.List.of(100.5, 101.2, 99.8));
            results.recordSuccess("producer", data);
            signal.signal(data);
        });

        // Consumer waits for signal, then processes
        Thread.ofVirtual().name("consumer").start(() -> {
            try {
                signal.await();
                consumerResult.set(signal.payload());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(200);
        assertThat(consumerResult.get()).isNotNull();
        assertThat(consumerResult.get()).isInstanceOf(Map.class);

        scope.close();
    }

    @Test
    void race_firstFeedWins() throws InterruptedException {
        var scope = new DefaultScenarioScope();
        var primarySignal = scope.signal("primary-feed");
        var backupSignal = scope.signal("backup-feed");
        var winner = new AtomicReference<String>();
        var ready = new CountDownLatch(1);

        // Primary feed — fast (50ms)
        Thread.ofVirtual().name("primary-feed").start(() -> {
            try {
                ready.await();
                Thread.sleep(50);
                primarySignal.signal(Map.of("source", "primary", "price", 100.5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Backup feed — slow (200ms)
        Thread.ofVirtual().name("backup-feed").start(() -> {
            try {
                ready.await();
                Thread.sleep(200);
                backupSignal.signal(Map.of("source", "backup", "price", 100.4));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Race: wait for first completion
        ready.countDown();
        Thread.ofVirtual().name("race-waiter").start(() -> {
            try {
                // In real orchestration, this would be a single race: [primary, backup]
                // Here we simulate by awaiting whichever signals first
                while (!primarySignal.isSignalled() && !backupSignal.isSignalled()) {
                    Thread.sleep(10);
                }
                if (primarySignal.isSignalled()) {
                    winner.set("primary");
                } else {
                    winner.set("backup");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).join(2000);

        assertThat(winner.get()).isEqualTo("primary");

        scope.close();
    }
}
