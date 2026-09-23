package io.casehub.yaml.core.orchestration;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class DefaultOrcSemaphore implements OrcSemaphore {

    private final String name;
    private final int maxPermits;
    private final Semaphore semaphore;
    private final AtomicReference<String> owner;
    private final ScheduledExecutorService replenisher;

    public DefaultOrcSemaphore(String name, int permits) {
        this(name, permits, null);
    }

    public DefaultOrcSemaphore(String name, int permits, Duration window) {
        this.name = name;
        this.maxPermits = permits;
        this.semaphore = new Semaphore(permits, true);
        this.owner = permits == 1 ? new AtomicReference<>() : null;

        if (window != null && !window.isZero()) {
            this.replenisher = Executors.newSingleThreadScheduledExecutor(
                    Thread.ofVirtual().name("semaphore-" + name + "-replenish").factory());
            long periodMs = window.toMillis();
            this.replenisher.scheduleAtFixedRate(() -> {
                int available = semaphore.availablePermits();
                int toRelease = maxPermits - available;
                if (toRelease > 0) {
                    semaphore.release(toRelease);
                }
            }, periodMs, periodMs, TimeUnit.MILLISECONDS);
        } else {
            this.replenisher = null;
        }
    }

    @Override
    public void acquire() throws InterruptedException {
        checkReentrancy();
        semaphore.acquire();
        setOwner();
    }

    @Override
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        checkReentrancy();
        boolean acquired = semaphore.tryAcquire(timeout, unit);
        if (acquired) setOwner();
        return acquired;
    }

    @Override
    public void release() {
        clearOwner();
        semaphore.release();
    }

    @Override
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    public void shutdown() {
        if (replenisher != null) {
            replenisher.shutdownNow();
        }
    }

    private void checkReentrancy() {
        if (owner == null) return;
        String currentContext = stepContext();
        if (currentContext != null && currentContext.equals(owner.get())) {
            throw new SemaphoreReentrancyException(name, currentContext);
        }
    }

    private void setOwner() {
        if (owner != null) {
            owner.set(stepContext());
        }
    }

    private void clearOwner() {
        if (owner != null) {
            owner.set(null);
        }
    }

    private static String stepContext() {
        return Thread.currentThread().getName();
    }

    @Override
    public void releaseForClose() {
        shutdown();
    }
}
