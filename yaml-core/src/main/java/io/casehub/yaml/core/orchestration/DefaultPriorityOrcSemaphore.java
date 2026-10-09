package io.casehub.yaml.core.orchestration;

import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class DefaultPriorityOrcSemaphore implements PriorityOrcSemaphore {

    private final ReentrantLock lock = new ReentrantLock(true);
    private int permits;
    private final PriorityQueue<Waiter> waiters = new PriorityQueue<>();

    public DefaultPriorityOrcSemaphore(int permits) {
        if (permits < 1) throw new IllegalArgumentException("permits must be >= 1");
        this.permits = permits;
    }

    @Override
    public void acquire(Priority priority) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            if (permits > 0) {
                permits--;
                return;
            }
            var waiter = new Waiter(priority, lock.newCondition());
            waiters.add(waiter);
            while (!waiter.acquired) {
                waiter.condition.await();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean tryAcquire(Priority priority, long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        lock.lockInterruptibly();
        try {
            if (permits > 0) {
                permits--;
                return true;
            }
            var waiter = new Waiter(priority, lock.newCondition());
            waiters.add(waiter);
            while (!waiter.acquired) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    waiters.remove(waiter);
                    return false;
                }
                waiter.condition.awaitNanos(remaining);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release() {
        lock.lock();
        try {
            Waiter next = waiters.poll();
            if (next != null) {
                next.acquired = true;
                next.condition.signal();
            } else {
                permits++;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int availablePermits() {
        lock.lock();
        try {
            return permits;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void releaseForClose() {
        lock.lock();
        try {
            for (var waiter : waiters) {
                waiter.acquired = true;
                waiter.condition.signal();
            }
            waiters.clear();
        } finally {
            lock.unlock();
        }
    }

    private static final class Waiter implements Comparable<Waiter> {
        final Priority priority;
        final Condition condition;
        volatile boolean acquired;

        Waiter(Priority priority, Condition condition) {
            this.priority = priority;
            this.condition = condition;
        }

        @Override
        public int compareTo(Waiter other) {
            return Integer.compare(other.priority.level(), this.priority.level());
        }
    }
}
