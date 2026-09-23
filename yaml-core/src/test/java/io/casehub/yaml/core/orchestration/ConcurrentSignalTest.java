package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentSignalTest {

    @Test
    void signalAndAwait_safeUnderContention() throws InterruptedException {
        var signal = new DefaultOrcSignal();
        var waitersReady = new CountDownLatch(10);
        var waitersDone = new CountDownLatch(10);
        var payloads = new AtomicReference[10];

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            payloads[idx] = new AtomicReference<>();
            Thread.ofVirtual().name("waiter-" + i).start(() -> {
                try {
                    waitersReady.countDown();
                    signal.await();
                    payloads[idx].set(signal.payload());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    waitersDone.countDown();
                }
            });
        }

        waitersReady.await();
        Thread.sleep(20);
        signal.signal("broadcast-payload");
        boolean done = waitersDone.await(2, TimeUnit.SECONDS);
        assertThat(done).isTrue();

        for (int i = 0; i < 10; i++) {
            assertThat(payloads[i].get()).isEqualTo("broadcast-payload");
        }
    }
}
