package io.casehub.platform.agent.gate;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AimdAdmissionStrategyTest {

    @Test
    void acquireAndRelease() throws Exception {
        var strategy = new AimdAdmissionStrategy(4, 3, Duration.ofSeconds(10));
        assertEquals(AdmissionStrategy.Scope.INVOCATION, strategy.scope());
        assertEquals(0, strategy.activeCount());
        assertEquals(4, strategy.currentWindow());

        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        assertEquals(1, strategy.activeCount());

        strategy.release();
        assertEquals(0, strategy.activeCount());
    }

    @Test
    void acquireTimesOutWhenNoPermitsAvailable() throws Exception {
        var strategy = new AimdAdmissionStrategy(1, 5, Duration.ofSeconds(10));
        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        assertFalse(strategy.tryAcquire(Duration.ofMillis(50)));
        strategy.release();
    }

    @Test
    void windowNeverExceedsCeiling() throws Exception {
        var strategy = new AimdAdmissionStrategy(4, 1, Duration.ofSeconds(10));
        for (int i = 0; i < 20; i++) {
            assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
            strategy.release();
        }
        assertEquals(4, strategy.currentWindow());
    }

    @Test
    void multiplicativeDecreaseOnRollback() throws Exception {
        var strategy = new AimdAdmissionStrategy(12, 5, Duration.ofSeconds(10));
        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback();
        assertEquals(6, strategy.currentWindow());

        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback();
        assertEquals(3, strategy.currentWindow());

        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback();
        assertEquals(1, strategy.currentWindow());

        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback();
        assertEquals(1, strategy.currentWindow());
    }

    @Test
    void congestionAvoidanceGrowsByOneAtOrAboveHalfCeiling() throws Exception {
        var strategy = new AimdAdmissionStrategy(8, 3, Duration.ofSeconds(10));
        // Force window to 4 (half of ceiling)
        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback(); // 8 → 4
        assertEquals(4, strategy.currentWindow());

        // 3 successes → congestion avoidance (4 >= 8/2): +1 → 5
        for (int i = 0; i < 3; i++) {
            assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
            strategy.release();
        }
        assertEquals(5, strategy.currentWindow());
    }

    @Test
    void slowStartDoublesWindowBelowHalfCeiling() throws Exception {
        var strategy = new AimdAdmissionStrategy(12, 2, Duration.ofSeconds(10));
        // Crash window to 1
        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback(); // 12 → 6
        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback(); // 6 → 3
        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
        strategy.rollback(); // 3 → 1
        assertEquals(1, strategy.currentWindow());

        // Slow start: 1 < 12/2=6, so double: 1→2
        for (int i = 0; i < 2; i++) {
            assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
            strategy.release();
        }
        assertEquals(2, strategy.currentWindow());

        // 2 more successes → 2→4
        for (int i = 0; i < 2; i++) {
            assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));
            strategy.release();
        }
        assertEquals(4, strategy.currentWindow());
    }

    @Test
    void priorityAcquireServedBeforeNormal() throws Exception {
        var strategy = new AimdAdmissionStrategy(1, 5, Duration.ofSeconds(10));
        assertTrue(strategy.tryAcquire(Duration.ofMillis(100)));

        var order = new CopyOnWriteArrayList<String>();

        var normal = Thread.ofVirtual().start(() -> {
            try {
                strategy.tryAcquire(Duration.ofSeconds(5));
                order.add("normal");
                strategy.release();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread.sleep(50);

        var priority = Thread.ofVirtual().start(() -> {
            try {
                strategy.tryAcquirePriority(Duration.ofSeconds(5));
                order.add("priority");
                strategy.release();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread.sleep(50);

        strategy.release();
        normal.join(2000);
        priority.join(2000);

        assertEquals(List.of("priority", "normal"), order);
    }

    @Test
    void concurrentStressNoPermitLeak() throws Exception {
        var strategy = new AimdAdmissionStrategy(8, 3, Duration.ofSeconds(10));
        int threads = 20;
        int opsPerThread = 100;
        var latch = new CountDownLatch(threads);
        var errors = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            int tid = t;
            Thread.ofVirtual().start(() -> {
                try {
                    for (int i = 0; i < opsPerThread; i++) {
                        if (strategy.tryAcquire(Duration.ofMillis(500))) {
                            Thread.sleep(1);
                            if (i % 10 == 0) {
                                strategy.rollback();
                            } else {
                                strategy.release();
                            }
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(0, errors.get());
        assertEquals(0, strategy.activeCount());
    }
}
