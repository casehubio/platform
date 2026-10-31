package io.casehub.platform.agent.gate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class SalienceScheduler {

    private final int maxActivePrompts;
    private final Map<String, PoolEntry> pools = new ConcurrentHashMap<>();
    private final AtomicInteger totalActive = new AtomicInteger();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition slotAvailable = lock.newCondition();

    public SalienceScheduler(int maxActivePrompts) {
        if (maxActivePrompts < 1) {
            throw new IllegalArgumentException("maxActivePrompts must be >= 1");
        }
        this.maxActivePrompts = maxActivePrompts;
    }

    public void register(String poolName, int salience) {
        pools.put(poolName, new PoolEntry(poolName, salience));
    }

    public boolean tryAcquire(String poolName, Duration timeout) throws InterruptedException {
        var entry = pools.get(poolName);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown pool: " + poolName);
        }

        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        lock.lockInterruptibly();
        try {
            while (!canAcquire(entry)) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) return false;
                if (!slotAvailable.await(remaining, TimeUnit.NANOSECONDS)) {
                    return false;
                }
            }
            entry.activeCount.incrementAndGet();
            totalActive.incrementAndGet();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public void release(String poolName) {
        var entry = pools.get(poolName);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown pool: " + poolName);
        }

        lock.lock();
        try {
            entry.activeCount.decrementAndGet();
            totalActive.decrementAndGet();
            slotAvailable.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int highestActiveSalience() {
        return pools.values().stream()
                .filter(p -> p.activeCount.get() > 0)
                .mapToInt(p -> p.salience)
                .max().orElse(0);
    }

    private boolean canAcquire(PoolEntry entry) {
        if (totalActive.get() >= maxActivePrompts) {return false;}

        int highestActive = highestActiveSalience();
        if (entry.salience >= highestActive) {return true;}

        return entry.activeCount.get() < guaranteedMin();
    }

    private int guaranteedMin() {
        int poolCount = pools.size();
        return poolCount == 0 ? 0 : Math.max(1, maxActivePrompts / poolCount);
    }

    private static class PoolEntry {
        final String name;
        final int salience;
        final AtomicInteger activeCount = new AtomicInteger();

        PoolEntry(String name, int salience) {
            this.name = name;
            this.salience = salience;
        }
    }
}
