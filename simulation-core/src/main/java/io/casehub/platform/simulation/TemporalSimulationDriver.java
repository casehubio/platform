package io.casehub.platform.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TemporalSimulationDriver<E> {

    private static final int MAX_FAILURES = 100;

    private final TemporalEventSink<E> eventSink;
    private final SimulationRuntime simulation;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition pauseCondition = lock.newCondition();

    private volatile State state = State.IDLE;
    private volatile double speed;
    private volatile Thread driverThread;
    private volatile DriverResult lastResult;

    public enum State { IDLE, RUNNING, PAUSED, STOPPED, COMPLETED }

    public TemporalSimulationDriver(TemporalEventSink<E> eventSink, SimulationRuntime simulation) {
        this.eventSink = eventSink;
        this.simulation = simulation;
    }

    public TemporalSimulationDriver(TemporalEventSink<E> eventSink) {
        this(eventSink, null);
    }

    public void start(TemporalProfile<E> profile) {
        lock.lock();
        try {
            if (state != State.IDLE) {
                throw new IllegalStateException("Driver is " + state + ", expected IDLE");
            }
            speed = profile.speed();
            state = State.RUNNING;
            driverThread = Thread.ofVirtual()
                    .name("temporal-driver-" + profile.name())
                    .start(() -> runLoop(profile));
        } finally {
            lock.unlock();
        }
    }

    public void pause() {
        lock.lock();
        try {
            if (state == State.RUNNING) {
                state = State.PAUSED;
            }
        } finally {
            lock.unlock();
        }
    }

    public void resume() {
        lock.lock();
        try {
            if (state == State.PAUSED) {
                state = State.RUNNING;
                pauseCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            if (state == State.RUNNING || state == State.PAUSED || state == State.COMPLETED) {
                state = State.STOPPED;
                pauseCondition.signalAll();
                if (driverThread != null) {
                    driverThread.interrupt();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void setSpeed(double speed) {
        if (speed <= 0) throw new IllegalArgumentException("speed must be positive");
        this.speed = speed;
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public DriverResult lastResult() {
        return lastResult;
    }

    private void runLoop(TemporalProfile<E> profile) {
        int emittedCount = 0;
        int failureCount = 0;
        int loopIterations = 0;
        List<DriverFailure> failures = new ArrayList<>();

        try {
            do {
                List<TimedEntry<E>> entries = profile.sequence().entries();
                for (int i = 0; i < entries.size(); i++) {
                    checkPauseOrStop();
                    if (state == State.STOPPED) break;

                    TimedEntry<E> entry = entries.get(i);

                    if (!entry.delay().isZero()) {
                        long delayMs = (long) (entry.delay().toMillis() / speed);
                        if (delayMs > 0) {
                            Thread.sleep(delayMs);
                        }
                    }

                    checkPauseOrStop();
                    if (state == State.STOPPED) break;

                    String effectiveQN = entry.qualifiedName() != null
                            ? entry.qualifiedName() : profile.qualifiedName();

                    try {
                        eventSink.deliver(effectiveQN, entry.label(), entry.event());
                        emittedCount++;

                        if (simulation != null) {
                            simulation.recordJournal(effectiveQN,
                                    profile.tenancyId(), entry.label(),
                                    entry.event(), true);
                        }
                    } catch (Exception e) {
                        failureCount++;
                        if (failures.size() < MAX_FAILURES) {
                            failures.add(new DriverFailure(i, entry.label(), e));
                        }
                    }
                }

                if (state != State.STOPPED) {
                    loopIterations++;
                }

            } while (profile.loop() && state != State.STOPPED);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lastResult = new DriverResult(emittedCount, failureCount,
                loopIterations, failures);

        lock.lock();
        try {
            if (state != State.STOPPED) {
                state = State.COMPLETED;
            }
        } finally {
            lock.unlock();
        }
    }

    private void checkPauseOrStop() throws InterruptedException {
        lock.lock();
        try {
            while (state == State.PAUSED) {
                pauseCondition.await();
            }
        } finally {
            lock.unlock();
        }
    }
}
