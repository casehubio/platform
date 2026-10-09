package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PriorityOrcSemaphoreTest {

    @Test
    void acquireAndRelease() throws InterruptedException {
        var sem = new DefaultPriorityOrcSemaphore(1);
        sem.acquire(Priority.NORMAL);
        assertEquals(0, sem.availablePermits());
        sem.release();
        assertEquals(1, sem.availablePermits());
    }

    @Test
    void higherPriorityAcquiresFirst() throws Exception {
        var sem = new DefaultPriorityOrcSemaphore(1);
        sem.acquire(Priority.NORMAL); // hold the permit

        var order = Collections.synchronizedList(new ArrayList<String>());
        var ready = new CountDownLatch(2);

        Thread bgThread = Thread.ofVirtual().start(() -> {
            try {
                ready.countDown();
                sem.acquire(Priority.BACKGROUND);
                order.add("background");
                sem.release();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread highThread = Thread.ofVirtual().start(() -> {
            try {
                ready.countDown();
                sem.acquire(Priority.HIGH);
                order.add("high");
                sem.release();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        ready.await(1, TimeUnit.SECONDS);
        Thread.sleep(50); // let both threads enqueue
        sem.release(); // release the initial hold

        bgThread.join(2000);
        highThread.join(2000);

        assertEquals(List.of("high", "background"), order);
    }

    @Test
    void tryAcquireRespectsTimeout() throws InterruptedException {
        var sem = new DefaultPriorityOrcSemaphore(1);
        sem.acquire(Priority.NORMAL);
        assertFalse(sem.tryAcquire(Priority.HIGH, 50, TimeUnit.MILLISECONDS));
        sem.release();
    }

    @Test
    void acquireIsInterruptible() throws InterruptedException {
        var sem = new DefaultPriorityOrcSemaphore(1);
        sem.acquire(Priority.NORMAL);

        var interrupted = new CountDownLatch(1);
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                sem.acquire(Priority.HIGH);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        });

        Thread.sleep(50);
        t.interrupt();
        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
    }

    @Test
    void multiplePermits() throws InterruptedException {
        var sem = new DefaultPriorityOrcSemaphore(3);
        sem.acquire(Priority.NORMAL);
        sem.acquire(Priority.NORMAL);
        sem.acquire(Priority.NORMAL);
        assertEquals(0, sem.availablePermits());
        sem.release();
        assertEquals(1, sem.availablePermits());
    }

    @Test
    void releaseForCloseUnblocksWaiters() throws Exception {
        var sem = new DefaultPriorityOrcSemaphore(1);
        sem.acquire(Priority.NORMAL);

        var unblocked = new CountDownLatch(1);
        Thread t = Thread.ofVirtual().start(() -> {
            try {
                sem.acquire(Priority.HIGH);
                unblocked.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(50);
        sem.releaseForClose();
        assertTrue(unblocked.await(1, TimeUnit.SECONDS));
    }
}
