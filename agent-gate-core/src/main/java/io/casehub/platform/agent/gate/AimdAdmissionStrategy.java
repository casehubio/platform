package io.casehub.platform.agent.gate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class AimdAdmissionStrategy implements AdmissionStrategy {

    private final int ceiling;
    private final int increaseThreshold;
    private final Duration normalAgingTimeout;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition priorityWaiters = lock.newCondition();
    private final Condition normalWaiters = lock.newCondition();

    private int window;
    private int available;
    private int active;
    private int successCount;
    private int priorityWaiting;

    public AimdAdmissionStrategy(int ceiling, int increaseThreshold,
                                  Duration normalAgingTimeout) {
        if (ceiling < 1) throw new IllegalArgumentException("ceiling must be >= 1");
        if (increaseThreshold < 1) throw new IllegalArgumentException("increaseThreshold must be >= 1");
        this.ceiling = ceiling;
        this.increaseThreshold = increaseThreshold;
        this.normalAgingTimeout = normalAgingTimeout;
        this.window = ceiling;
        this.available = ceiling;
    }

    @Override
    public Scope scope() {
        return Scope.INVOCATION;
    }

    @Override
    public boolean tryAcquire(Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        lock.lockInterruptibly();
        try {
            while (available <= 0 || priorityWaiting > 0) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) return false;
                if (!normalWaiters.await(remaining, TimeUnit.NANOSECONDS)) {
                    return false;
                }
            }
            available--;
            active++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean tryAcquirePriority(Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        lock.lockInterruptibly();
        try {
            priorityWaiting++;
            try {
                while (available <= 0) {
                    long remaining = deadlineNanos - System.nanoTime();
                    if (remaining <= 0) return false;
                    if (!priorityWaiters.await(remaining, TimeUnit.NANOSECONDS)) {
                        return false;
                    }
                }
            } finally {
                priorityWaiting--;
            }
            available--;
            active++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release() {
        lock.lock();
        try {
            active--;
            available++;
            successCount++;
            if (successCount >= increaseThreshold && window < ceiling) {
                if (window < ceiling / 2) {
                    int growth = Math.min(window, ceiling - window);
                    window += growth;
                    available += growth;
                } else {
                    window++;
                    available++;
                }
                successCount = 0;
            }
            signalWaiters();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rollback() {
        lock.lock();
        try {
            active--;
            available++;
            successCount = 0;
            int newWindow = Math.max(1, window / 2);
            int reduction = window - newWindow;
            window = newWindow;
            available = Math.max(0, available - reduction);
            signalWaiters();
        } finally {
            lock.unlock();
        }
    }

    private void signalWaiters() {
        if (priorityWaiting > 0) {
            priorityWaiters.signal();
        } else {
            normalWaiters.signal();
        }
    }


    public int availablePermits() {
        lock.lock();
        try {
            return available;
        } finally {
            lock.unlock();
        }
    }

    public int activeCount() {
        lock.lock();
        try {
            return active;
        } finally {
            lock.unlock();
        }
    }

    public int currentWindow() {
        lock.lock();
        try {
            return window;
        } finally {
            lock.unlock();
        }
    }

    public int ceiling() {
        return ceiling;
    }
}
