package io.casehub.yaml.core.orchestration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentStateMachineTest {

    enum RaceState { PENDING, APPROVED, REJECTED }

    @Test
    void atomicTransition_noDuplicateStates() throws InterruptedException {
        var sm = DefaultOrcStateMachine.builder("race", RaceState.class, RaceState.PENDING)
                .transition(RaceState.PENDING, RaceState.APPROVED)
                .transition(RaceState.PENDING, RaceState.REJECTED)
                .terminal(RaceState.APPROVED, RaceState.REJECTED)
                .build();

        var winners = new AtomicInteger(0);
        var ready = new CountDownLatch(10);
        var done = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            final RaceState target = i % 2 == 0 ? RaceState.APPROVED : RaceState.REJECTED;
            Thread.ofVirtual().name("racer-" + i).start(() -> {
                ready.countDown();
                try {
                    ready.await();
                    if (sm.transition(RaceState.PENDING, target)) {
                        winners.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        done.await(5, TimeUnit.SECONDS);
        assertThat(winners.get()).isEqualTo(1);
        assertThat(sm.currentState()).isIn(RaceState.APPROVED, RaceState.REJECTED);
    }

    @Test
    void competingTransitions_exactlyOneWins() throws InterruptedException {
        var sm = DefaultOrcStateMachine.builder("compete", RaceState.class, RaceState.PENDING)
                .transition(RaceState.PENDING, RaceState.APPROVED)
                .terminal(RaceState.APPROVED)
                .build();

        var successCount = new AtomicInteger(0);
        var done = new CountDownLatch(20);

        for (int i = 0; i < 20; i++) {
            Thread.ofVirtual().name("competitor-" + i).start(() -> {
                if (sm.transition(RaceState.PENDING, RaceState.APPROVED)) {
                    successCount.incrementAndGet();
                }
                done.countDown();
            });
        }

        done.await(5, TimeUnit.SECONDS);
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(sm.currentState()).isEqualTo(RaceState.APPROVED);
    }

    @Test
    void observersSeeCommittedStateOnly() throws InterruptedException {
        var sm = DefaultOrcStateMachine.builder("observe", RaceState.class, RaceState.PENDING)
                .transition(RaceState.PENDING, RaceState.APPROVED)
                .terminal(RaceState.APPROVED)
                .build();

        var observedStates = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<RaceState, Boolean>());
        var done = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            Thread.ofVirtual().name("observer-" + i).start(() -> {
                observedStates.add(sm.currentState());
                done.countDown();
            });
        }

        sm.transition(RaceState.PENDING, RaceState.APPROVED);
        done.await(5, TimeUnit.SECONDS);

        for (RaceState s : observedStates) {
            assertThat(s).isIn(RaceState.PENDING, RaceState.APPROVED);
        }
    }
}
