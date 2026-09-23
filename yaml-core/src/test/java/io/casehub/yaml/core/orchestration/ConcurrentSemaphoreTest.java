package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentSemaphoreTest {

    @Test
    void multipleThreadsRespectPermitLimit() throws InterruptedException {
        var sem = new DefaultOrcSemaphore("api", 3);
        var maxConcurrent = new AtomicInteger(0);
        var current = new AtomicInteger(0);
        var startLatch = new CountDownLatch(1);
        var doneLatch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            Thread.ofVirtual().name("worker-" + i).start(() -> {
                try {
                    startLatch.await();
                    sem.acquire();
                    int c = current.incrementAndGet();
                    maxConcurrent.updateAndGet(max -> Math.max(max, c));
                    Thread.sleep(10);
                    current.decrementAndGet();
                    sem.release();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        assertThat(maxConcurrent.get()).isLessThanOrEqualTo(3);
    }

    @Test
    void noDeadlockUnderContention() throws InterruptedException {
        var sem = new DefaultOrcSemaphore("tight", 2);
        var doneLatch = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            Thread.ofVirtual().name("contender-" + i).start(() -> {
                try {
                    for (int j = 0; j < 50; j++) {
                        sem.acquire();
                        sem.release();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean completed = doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(completed).isTrue();
    }
}
