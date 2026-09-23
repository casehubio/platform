package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentLatchTest {

    @Test
    void multipleCountdowns_safeUnderContention() throws InterruptedException {
        var latch = new DefaultOrcLatch(5);
        var waiterReady = new CountDownLatch(1);
        var waiterDone = new AtomicBoolean(false);

        Thread.ofVirtual().name("waiter").start(() -> {
            try {
                waiterReady.countDown();
                latch.await();
                waiterDone.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        waiterReady.await();
        var doneLatch = new CountDownLatch(5);
        for (int i = 0; i < 5; i++) {
            Thread.ofVirtual().name("countdown-" + i).start(() -> {
                latch.countDown();
                doneLatch.countDown();
            });
        }

        doneLatch.await(2, TimeUnit.SECONDS);
        Thread.sleep(50);
        assertThat(waiterDone.get()).isTrue();
    }

    @Test
    void awaitAndCountdown_noDeadlock() throws InterruptedException {
        var latch = new DefaultOrcLatch(10);
        var done = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            Thread.ofVirtual().name("worker-" + i).start(() -> {
                try {
                    Thread.sleep((long) (Math.random() * 10));
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        boolean completed = latch.await(2, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        done.await(2, TimeUnit.SECONDS);
    }
}
