package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class OrcSignalTest {

    @Test
    void signalAndAwait_basic() throws InterruptedException {
        var signal = new DefaultOrcSignal();
        signal.signal("hello");
        signal.await();
        assertThat(signal.payload()).isEqualTo("hello");
    }

    @Test
    void awaitBeforeSignal_blocksUntilSignalled() throws Exception {
        var signal = new DefaultOrcSignal();
        var unblocked = new AtomicBoolean(false);
        var t = Thread.ofVirtual().name("waiter").start(() -> {
            try {
                signal.await();
                unblocked.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(50);
        assertThat(unblocked.get()).isFalse();
        signal.signal("data");
        t.join(1000);
        assertThat(unblocked.get()).isTrue();
        assertThat(signal.payload()).isEqualTo("data");
    }

    @Test
    void withPayload_payloadAccessible() throws InterruptedException {
        var signal = new DefaultOrcSignal();
        signal.signal(42);
        signal.await();
        assertThat(signal.payload()).isEqualTo(42);
    }

    @Test
    void oneShotSignal_secondSignalIgnored() {
        var signal = new DefaultOrcSignal();
        signal.signal("first");
        signal.signal("second");
        assertThat(signal.payload()).isEqualTo("second");
        assertThat(signal.isSignalled()).isTrue();
    }

    @Test
    void alreadySignalled_awaitReturnsImmediately() throws InterruptedException {
        var signal = new DefaultOrcSignal();
        signal.signal();
        boolean result = signal.await(10, TimeUnit.MILLISECONDS);
        assertThat(result).isTrue();
    }

    @Test
    void payloadRetention_accessibleAfterSignal() {
        var signal = new DefaultOrcSignal();
        signal.signal("retained");
        assertThat(signal.isSignalled()).isTrue();
        assertThat(signal.payload()).isEqualTo("retained");
    }

    @Test
    void repeatableSignal_multipleSignals_latestPayload() {
        var signal = new DefaultOrcSignal(true);
        signal.signal("v1");
        signal.signal("v2");
        signal.signal("v3");
        assertThat(signal.payload()).isEqualTo("v3");
    }

    @Test
    void repeatableSignal_multipleWaiters_allUnblocked() throws Exception {
        var signal = new DefaultOrcSignal(true);
        var count = new java.util.concurrent.atomic.AtomicInteger(0);
        var ready = new java.util.concurrent.CountDownLatch(3);
        var done = new java.util.concurrent.CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            Thread.ofVirtual().name("waiter-" + i).start(() -> {
                try {
                    ready.countDown();
                    signal.await();
                    count.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        Thread.sleep(50);
        signal.signal("broadcast");
        done.await(2, TimeUnit.SECONDS);
        assertThat(count.get()).isEqualTo(3);
    }
}
