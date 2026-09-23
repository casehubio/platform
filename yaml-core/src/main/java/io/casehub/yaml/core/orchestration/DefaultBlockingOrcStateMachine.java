package io.casehub.yaml.core.orchestration;

import io.casehub.yaml.core.runtime.SpeedMultiplier;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public final class DefaultBlockingOrcStateMachine<S extends Enum<S>> implements BlockingOrcStateMachine<S> {

    private final OrcStateMachine<S> delegate;
    private final SpeedMultiplier speedMultiplier;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition stateChanged = lock.newCondition();
    private volatile S lastFrom;
    private volatile S lastTo;
    private volatile boolean released = false;

    public DefaultBlockingOrcStateMachine(OrcStateMachine<S> delegate, SpeedMultiplier speedMultiplier) {
        this.delegate = delegate;
        this.speedMultiplier = speedMultiplier;
    }

    public DefaultBlockingOrcStateMachine(OrcStateMachine<S> delegate) {
        this(delegate, SpeedMultiplier.identity());
    }

    @Override
    public S currentState() {
        return delegate.currentState();
    }

    @Override
    public boolean transition(S from, S to) {
        return transition(from, to, null);
    }

    @Override
    public boolean transition(S from, S to, Object payload) {
        boolean result = delegate.transition(from, to, payload);
        if (result) {
            lock.lock();
            try {
                lastFrom = from;
                lastTo = to;
                stateChanged.signalAll();
            } finally {
                lock.unlock();
            }
        }
        return result;
    }

    @Override
    public void onTransition(S from, S to, TransitionHandler handler) {
        delegate.onTransition(from, to, handler);
    }

    @Override
    public void onEnter(S state, StateHandler handler) {
        delegate.onEnter(state, handler);
    }

    @Override
    public void onExit(S state, StateHandler handler) {
        delegate.onExit(state, handler);
    }

    @Override
    public void awaitState(S target) throws InterruptedException {
        lock.lock();
        try {
            while (currentState() != target) {
                if (released) throw new InterruptedException("state machine released");
                stateChanged.await();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean awaitState(S target, Duration timeout) throws InterruptedException {
        long adjustedNanos = adjustForSpeed(timeout);
        long deadline = System.nanoTime() + adjustedNanos;
        lock.lock();
        try {
            while (currentState() != target) {
                if (released) return false;
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                stateChanged.await(remaining, TimeUnit.NANOSECONDS);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void awaitTransition(S from, S to) throws InterruptedException {
        lock.lock();
        try {
            while (!(lastFrom == from && lastTo == to)) {
                if (released) throw new InterruptedException("state machine released");
                stateChanged.await();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public S awaitAnyState(java.util.Set<S> targets) throws InterruptedException {
        lock.lock();
        try {
            while (!targets.contains(currentState())) {
                if (released) {throw new InterruptedException("state machine released");}
                stateChanged.await();
            }
            return currentState();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public S awaitAnyState(java.util.Set<S> targets, Duration timeout) throws InterruptedException {
        long adjustedNanos = adjustForSpeed(timeout);
        long deadline      = System.nanoTime() + adjustedNanos;
        lock.lock();
        try {
            while (!targets.contains(currentState())) {
                if (released) {return currentState();}
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {return currentState();}
                stateChanged.await(remaining, TimeUnit.NANOSECONDS);
            }
            return currentState();
        } finally {
            lock.unlock();
        }
    }


    private long adjustForSpeed(Duration timeout) {
        double speed = speedMultiplier.currentSpeed();
        if (speed <= 0) speed = 1.0;
        return (long) (timeout.toNanos() / speed);
    }

    @Override
    public void releaseForClose() {
        released = true;
        lock.lock();
        try {
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

}
