package io.casehub.yaml.core.orchestration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;

public final class DefaultOrcSignal implements OrcSignal {

    private final boolean repeatable;
    private final CompletableFuture<Object> oneShotFuture;
    private final AtomicReference<Object> latestPayload;
    private final ReentrantLock repeatLock;
    private final Condition repeatCondition;
    private volatile boolean signalled;

    public DefaultOrcSignal(boolean repeatable) {
        this.repeatable = repeatable;
        this.oneShotFuture = repeatable ? null : new CompletableFuture<>();
        this.latestPayload = new AtomicReference<>();
        this.repeatLock = repeatable ? new ReentrantLock() : null;
        this.repeatCondition = repeatable ? repeatLock.newCondition() : null;
        this.signalled = false;
    }

    public DefaultOrcSignal() {
        this(false);
    }

    @Override
    public void signal() {
        signal(null);
    }

    @Override
    public void signal(Object payload) {
        latestPayload.set(payload);
        signalled = true;
        if (repeatable) {
            repeatLock.lock();
            try {
                repeatCondition.signalAll();
            } finally {
                repeatLock.unlock();
            }
        } else {
            oneShotFuture.complete(payload);
        }
    }

    @Override
    public void await() throws InterruptedException {
        if (repeatable) {
            if (signalled) return;
            repeatLock.lockInterruptibly();
            try {
                while (!signalled) {
                    repeatCondition.await();
                }
            } finally {
                repeatLock.unlock();
            }
        } else {
            try {
                oneShotFuture.get();
            } catch (java.util.concurrent.ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        if (repeatable) {
            if (signalled) return true;
            repeatLock.lockInterruptibly();
            try {
                long nanos = unit.toNanos(timeout);
                while (!signalled) {
                    if (nanos <= 0) return false;
                    nanos = repeatCondition.awaitNanos(nanos);
                }
                return true;
            } finally {
                repeatLock.unlock();
            }
        } else {
            try {
                oneShotFuture.get(timeout, unit);
                return true;
            } catch (TimeoutException e) {
                return false;
            } catch (java.util.concurrent.ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    @Override
    public Object payload() {
        return latestPayload.get();
    }

    @Override
    public boolean isSignalled() {
        return signalled;
    }
}
