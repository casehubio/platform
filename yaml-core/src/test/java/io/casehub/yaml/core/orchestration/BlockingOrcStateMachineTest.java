package io.casehub.yaml.core.orchestration;

import io.casehub.yaml.core.runtime.SpeedMultiplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class BlockingOrcStateMachineTest {

    enum State { IDLE, RUNNING, PAUSED, STOPPED, COMPLETED }

    private BlockingOrcStateMachine<State> machine;

    @BeforeEach
    void setUp() {
        var delegate = DefaultOrcStateMachine.<State>builder("test", State.class, State.IDLE)
                .transition(State.IDLE, State.RUNNING)
                .transition(State.RUNNING, State.PAUSED)
                .transition(State.PAUSED, State.RUNNING)
                .transition(State.RUNNING, State.STOPPED)
                .transition(State.RUNNING, State.COMPLETED)
                .terminal(State.STOPPED, State.COMPLETED)
                .build();
        machine = new DefaultBlockingOrcStateMachine<>(delegate);
    }

    @Test
    void currentState_delegatesToBase() {
        assertThat(machine.currentState()).isEqualTo(State.IDLE);
    }

    @Test
    void transition_delegatesToBase() {
        boolean result = machine.transition(State.IDLE, State.RUNNING);
        assertThat(result).isTrue();
        assertThat(machine.currentState()).isEqualTo(State.RUNNING);
    }

    @Test
    void awaitState_alreadyInTarget_returnsImmediately() throws InterruptedException {
        machine.transition(State.IDLE, State.RUNNING);
        machine.awaitState(State.RUNNING);
        assertThat(machine.currentState()).isEqualTo(State.RUNNING);
    }

    @Test
    void awaitState_blocksUntilTransition() throws InterruptedException {
        var arrived = new AtomicBoolean(false);
        var started = new CountDownLatch(1);

        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                started.countDown();
                machine.awaitState(State.RUNNING);
                arrived.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        started.await(1, TimeUnit.SECONDS);
        Thread.sleep(50);
        assertThat(arrived.get()).isFalse();

        machine.transition(State.IDLE, State.RUNNING);
        waiter.join(1000);

        assertThat(arrived.get()).isTrue();
    }

    @Test
    void awaitState_withTimeout_returnsFalseOnTimeout() throws InterruptedException {
        boolean result = machine.awaitState(State.RUNNING, Duration.ofMillis(50));
        assertThat(result).isFalse();
        assertThat(machine.currentState()).isEqualTo(State.IDLE);
    }

    @Test
    void awaitState_withTimeout_returnsTrueWhenReached() throws InterruptedException {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(20);
                machine.transition(State.IDLE, State.RUNNING);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        boolean result = machine.awaitState(State.RUNNING, Duration.ofSeconds(1));
        assertThat(result).isTrue();
    }

    @Test
    void awaitState_speedMultiplier_halvesRealTimeout() throws InterruptedException {
        var delegate = DefaultOrcStateMachine.<State>builder("test", State.class, State.IDLE)
                .transition(State.IDLE, State.RUNNING)
                .build();
        var fast = new DefaultBlockingOrcStateMachine<>(delegate, () -> 2.0);

        long start = System.nanoTime();
        boolean result = fast.awaitState(State.RUNNING, Duration.ofMillis(200));
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        assertThat(result).isFalse();
        assertThat(elapsed).isLessThan(150);
    }

    @Test
    void awaitTransition_blocksUntilSpecificTransition() throws InterruptedException {
        var arrived = new AtomicBoolean(false);
        var started = new CountDownLatch(1);

        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                started.countDown();
                machine.awaitTransition(State.IDLE, State.RUNNING);
                arrived.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        started.await(1, TimeUnit.SECONDS);
        Thread.sleep(50);
        assertThat(arrived.get()).isFalse();

        machine.transition(State.IDLE, State.RUNNING);
        waiter.join(1000);

        assertThat(arrived.get()).isTrue();
    }

    @Test
    void awaitState_interruptThrowsInterruptedException() throws InterruptedException {
        Thread waiter = Thread.ofVirtual().start(() -> {
            try {
                machine.awaitState(State.RUNNING);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread.sleep(50);
        waiter.interrupt();
        waiter.join(1000);
        assertThat(waiter.isAlive()).isFalse();
    }

    @Test
    void speedMultiplier_identity_returnsOnePointZero() {
        assertThat(SpeedMultiplier.identity().currentSpeed()).isEqualTo(1.0);
    }
}
