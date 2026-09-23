package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrcLatchTest {

    @Test
    void countDown_decrementsCount() {
        var latch = new DefaultOrcLatch(3);
        assertThat(latch.getCount()).isEqualTo(3);
        latch.countDown();
        assertThat(latch.getCount()).isEqualTo(2);
        latch.countDown();
        assertThat(latch.getCount()).isEqualTo(1);
    }

    @Test
    void await_blocksUntilZero() throws Exception {
        var latch = new DefaultOrcLatch(1);
        var unblocked = new java.util.concurrent.atomic.AtomicBoolean(false);
        var t = Thread.ofVirtual().name("waiter").start(() -> {
            try {
                latch.await();
                unblocked.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        Thread.sleep(50);
        assertThat(unblocked.get()).isFalse();
        latch.countDown();
        t.join(1000);
        assertThat(unblocked.get()).isTrue();
    }

    @Test
    void await_timeout_returnsFalse() throws InterruptedException {
        var latch = new DefaultOrcLatch(1);
        boolean result = latch.await(50, TimeUnit.MILLISECONDS);
        assertThat(result).isFalse();
    }

    @Test
    void alreadyZero_awaitReturnsImmediately() throws InterruptedException {
        var latch = new DefaultOrcLatch(1);
        latch.countDown();
        boolean result = latch.await(10, TimeUnit.MILLISECONDS);
        assertThat(result).isTrue();
    }
}
