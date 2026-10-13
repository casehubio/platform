package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

class ConsumerHandleTest {

    @Test
    void startsRunning() {
        ConsumerHandle handle = new DefaultConsumerHandle("test");
        assertTrue(handle.isRunning());
        assertFalse(handle.isReleased());
    }

    @Test
    void pauseSetsNotRunning() {
        ConsumerHandle handle = new DefaultConsumerHandle("test");
        handle.pause();
        assertFalse(handle.isRunning());
    }

    @Test
    void resumeAfterPause() {
        ConsumerHandle handle = new DefaultConsumerHandle("test");
        handle.pause();
        handle.resume();
        assertTrue(handle.isRunning());
    }

    @Test
    void releaseForCloseSetsReleased() {
        ConsumerHandle handle = new DefaultConsumerHandle("test");
        handle.releaseForClose();
        assertTrue(handle.isReleased());
    }

    @Test
    void resumeUnparksPausedThread() throws Exception {
        ConsumerHandle handle = new DefaultConsumerHandle("test");
        handle.pause();
        var arrived = new CountDownLatch(1);
        var resumed = new CountDownLatch(1);

        Thread consumer = Thread.ofVirtual().start(() -> {
            handle.registerThread(Thread.currentThread());
            arrived.countDown();
            while (!handle.isRunning() && !handle.isReleased()) {
                LockSupport.park();
                if (Thread.interrupted()) return;
            }
            resumed.countDown();
        });

        assertTrue(arrived.await(1, TimeUnit.SECONDS));
        assertFalse(handle.isRunning());

        handle.resume();
        assertTrue(resumed.await(1, TimeUnit.SECONDS));
        assertTrue(handle.isRunning());
    }

    @Test
    void releaseForCloseUnparksPausedThread() throws Exception {
        ConsumerHandle handle = new DefaultConsumerHandle("test");
        handle.pause();
        var arrived = new CountDownLatch(1);
        var exited = new CountDownLatch(1);

        Thread consumer = Thread.ofVirtual().start(() -> {
            handle.registerThread(Thread.currentThread());
            arrived.countDown();
            while (!handle.isReleased()) {
                LockSupport.park();
                if (Thread.interrupted()) break;
            }
            exited.countDown();
        });

        assertTrue(arrived.await(1, TimeUnit.SECONDS));
        handle.releaseForClose();
        assertTrue(exited.await(1, TimeUnit.SECONDS));
        assertTrue(handle.isReleased());
    }
}
