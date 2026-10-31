package io.casehub.platform.agent.gate;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalienceSchedulerTest {

    private static final Duration SHORT = Duration.ofMillis(100);
    private static final Duration LONG = Duration.ofSeconds(1);

    @Test
    void defaultSalience_roundRobinFairness() throws InterruptedException {
        var scheduler = new SalienceScheduler(5);
        scheduler.register("poolA", 0);
        scheduler.register("poolB", 0);
        assertThat(scheduler.tryAcquire("poolA", LONG)).isTrue();
        assertThat(scheduler.tryAcquire("poolB", LONG)).isTrue();
    }

    @Test
    void elevatedSalience_blocksLowerWhileActive() throws InterruptedException {
        var scheduler = new SalienceScheduler(2);
        scheduler.register("voice", 1);
        scheduler.register("email", 0);
        assertThat(scheduler.tryAcquire("voice", LONG)).isTrue();
        assertThat(scheduler.tryAcquire("voice", LONG)).isTrue();
        // Email blocked — voice occupies both slots and has higher salience
        assertThat(scheduler.tryAcquire("email", SHORT)).isFalse();
        scheduler.release("voice");
        scheduler.release("voice");
        // Voice idle — email can now acquire
        assertThat(scheduler.tryAcquire("email", LONG)).isTrue();
    }

    @Test
    void guaranteedMinCapacity_preventsStarvation() throws InterruptedException {
        var scheduler = new SalienceScheduler(3);
        scheduler.register("voice", 1);
        scheduler.register("email", 0);
        // Voice takes 2 of 3 slots (1 reserved for email)
        assertThat(scheduler.tryAcquire("voice", LONG)).isTrue();
        assertThat(scheduler.tryAcquire("voice", LONG)).isTrue();
        // Email gets its guaranteed slot even while voice is active
        assertThat(scheduler.tryAcquire("email", LONG)).isTrue();
    }

    @Test
    void salienceCascade_yieldsOnIdle() throws InterruptedException {
        var scheduler = new SalienceScheduler(2);
        scheduler.register("voice", 1);
        scheduler.register("email", 0);
        scheduler.register("whatsapp", 0);
        assertThat(scheduler.tryAcquire("voice", LONG)).isTrue();
        scheduler.release("voice");
        // Voice idle — email and whatsapp share
        assertThat(scheduler.tryAcquire("email", LONG)).isTrue();
        assertThat(scheduler.tryAcquire("whatsapp", LONG)).isTrue();
    }

    @Test
    void singlePool_usesAllSlots() throws InterruptedException {
        var scheduler = new SalienceScheduler(3);
        scheduler.register("only", 0);
        assertThat(scheduler.tryAcquire("only", LONG)).isTrue();
        assertThat(scheduler.tryAcquire("only", LONG)).isTrue();
        assertThat(scheduler.tryAcquire("only", LONG)).isTrue();
        assertThat(scheduler.tryAcquire("only", SHORT)).isFalse();
    }

    @Test
    void release_signalsWaiters() throws InterruptedException {
        var scheduler = new SalienceScheduler(1);
        scheduler.register("pool", 0);
        assertThat(scheduler.tryAcquire("pool", LONG)).isTrue();
        // Start a thread that will release after a delay
        var thread = new Thread(() -> {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            scheduler.release("pool");
        });
        thread.start();
        assertThat(scheduler.tryAcquire("pool", LONG)).isTrue();
        scheduler.release("pool");
        thread.join();
    }

    @Test
    void unregisteredPool_throwsIllegalArgument() {
        var scheduler = new SalienceScheduler(5);
        assertThatThrownBy(() -> scheduler.tryAcquire("unknown", LONG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidMaxActivePrompts_throws() {
        assertThatThrownBy(() -> new SalienceScheduler(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeSalience_reflectsHighestActivePool() throws InterruptedException {
        var scheduler = new SalienceScheduler(5);
        scheduler.register("voice", 2);
        scheduler.register("email", 0);
        assertThat(scheduler.highestActiveSalience()).isZero();
        scheduler.tryAcquire("email", LONG);
        assertThat(scheduler.highestActiveSalience()).isZero();
        scheduler.tryAcquire("voice", LONG);
        assertThat(scheduler.highestActiveSalience()).isEqualTo(2);
        scheduler.release("voice");
        assertThat(scheduler.highestActiveSalience()).isZero();
    }
}
