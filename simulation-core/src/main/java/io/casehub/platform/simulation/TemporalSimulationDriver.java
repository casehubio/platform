package io.casehub.platform.simulation;

import io.casehub.yaml.core.orchestration.BlockingOrcStateMachine;
import io.casehub.yaml.core.orchestration.DefaultBlockingOrcStateMachine;
import io.casehub.yaml.core.orchestration.DefaultOrcStateMachine;
import io.casehub.yaml.core.runtime.SpeedMultiplier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class TemporalSimulationDriver<E> {

    private static final int MAX_FAILURES = 100;
    private static final Set<State> NON_PAUSED = EnumSet.of(State.RUNNING, State.STOPPED, State.COMPLETED);

    private final TemporalEventSink<E> eventSink;
    private final SimulationRuntime    simulation;
    private final BlockingOrcStateMachine<State> lifecycle;

    private volatile Double             localSpeedOverride;
    private volatile TemporalProfile<E> activeProfile;
    private volatile Thread             driverThread;
    private volatile DriverResult       lastResult;

    public enum State {IDLE, RUNNING, PAUSED, STOPPED, COMPLETED}

    public TemporalSimulationDriver(TemporalEventSink<E> eventSink, SimulationRuntime simulation) {
        this.eventSink  = eventSink;
        this.simulation = simulation;
        SpeedMultiplier speed = simulation != null ? simulation::globalSpeed : SpeedMultiplier.identity();
        this.lifecycle = createLifecycle(speed);
        lifecycle.onTransition(State.IDLE, State.RUNNING, payload -> {
            @SuppressWarnings("unchecked")
            TemporalProfile<E> profile = (TemporalProfile<E>) payload;
            activeProfile = profile;
            driverThread = Thread.ofVirtual()
                                 .name("temporal-driver-" + profile.name())
                                 .start(() -> runLoop(profile));
        });
    }

    public TemporalSimulationDriver(TemporalEventSink<E> eventSink) {
        this(eventSink, null);
    }

    private static BlockingOrcStateMachine<State> createLifecycle(SpeedMultiplier speed) {
        var sm = DefaultOrcStateMachine.<State>builder("temporal-driver", State.class, State.IDLE)
                .transition(State.IDLE, State.RUNNING)
                .transition(State.RUNNING, State.PAUSED)
                .transition(State.PAUSED, State.RUNNING)
                .transition(State.RUNNING, State.STOPPED)
                .transition(State.PAUSED, State.STOPPED)
                .transition(State.RUNNING, State.COMPLETED)
                .terminal(State.STOPPED, State.COMPLETED)
                .build();
        return new DefaultBlockingOrcStateMachine<>(sm, speed);
    }

    public void start(TemporalProfile<E> profile) {
        localSpeedOverride = null;
        if (!lifecycle.transition(State.IDLE, State.RUNNING, profile)) {
            throw new IllegalStateException("Driver is " + lifecycle.currentState() + ", expected IDLE");
        }
    }

    public void pause() {
        lifecycle.transition(State.RUNNING, State.PAUSED);
    }

    public void resume() {
        lifecycle.transition(State.PAUSED, State.RUNNING);
    }

    public void stop() {
        while (true) {
            State current = lifecycle.currentState();
            if (current != State.RUNNING && current != State.PAUSED) return;
            if (lifecycle.transition(current, State.STOPPED)) {
                if (driverThread != null) driverThread.interrupt();
                return;
            }
        }
    }

    public void setSpeed(double speed) {
        if (speed <= 0) {throw new IllegalArgumentException("speed must be positive");}
        this.localSpeedOverride = speed;
    }

    public void resetSpeed() {
        this.localSpeedOverride = null;
    }

    public double speed() {
        return effectiveSpeed();
    }

    private double effectiveSpeed() {
        Double local = localSpeedOverride;
        if (local != null) {return local;}
        double global       = (simulation != null) ? simulation.globalSpeed() : 1.0;
        double profileSpeed = (activeProfile != null) ? activeProfile.speed() : 1.0;
        return profileSpeed * global;
    }

    public BlockingOrcStateMachine<State> lifecycle() {
        return lifecycle;
    }

    public DriverResult lastResult() {
        return lastResult;
    }

    private void runLoop(TemporalProfile<E> profile) {
        int                 emittedCount   = 0;
        int                 failureCount   = 0;
        int                 loopIterations = 0;
        List<DriverFailure> failures       = new ArrayList<>();

        try {
            do {
                List<TimedEntry<E>> entries = profile.sequence().entries();
                for (int i = 0; i < entries.size(); i++) {
                    State s = checkPauseOrStop();
                    if (s != State.RUNNING) {break;}

                    TimedEntry<E> entry = entries.get(i);

                    if (!entry.delay().isZero()) {
                        long delayMs = (long) (entry.delay().toMillis() / effectiveSpeed());
                        if (delayMs > 0) {
                            Thread.sleep(delayMs);
                        }
                    }

                    if (lifecycle.currentState() != State.RUNNING) {break;}

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

                if (lifecycle.currentState() != State.STOPPED) {
                    loopIterations++;
                }

            } while (profile.loop() && lifecycle.currentState() == State.RUNNING);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        lastResult = new DriverResult(emittedCount, failureCount,
                                      loopIterations, failures);

        if (lifecycle.currentState() != State.STOPPED) {
            lifecycle.transition(State.RUNNING, State.COMPLETED);
        }
    }

    private State checkPauseOrStop() throws InterruptedException {
        return lifecycle.awaitAnyState(NON_PAUSED);
    }
}
