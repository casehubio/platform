package io.casehub.platform.simulation.selector;

import io.casehub.platform.simulation.CorpusSelectorConfig;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DurationSelectorTest {

    @Test
    void firstPhaseActiveBeforeDuration() {
        var clock = new AtomicLong(0);
        var selector = new DurationSelector(
                List.of(
                        new CorpusSelectorConfig.DurationPhase("normal", Duration.ofSeconds(10)),
                        new CorpusSelectorConfig.DurationPhase("degraded", null)),
                SpeedMultiplier.identity(),
                clock::get);

        assertThat(selector.activePhase()).isEqualTo("normal");
    }

    @Test
    void advancesAfterDuration() {
        var clock = new AtomicLong(0);
        var selector = new DurationSelector(
                List.of(
                        new CorpusSelectorConfig.DurationPhase("normal", Duration.ofSeconds(10)),
                        new CorpusSelectorConfig.DurationPhase("degraded", null)),
                SpeedMultiplier.identity(),
                clock::get);

        clock.set(Duration.ofSeconds(11).toNanos());
        assertThat(selector.activePhase()).isEqualTo("degraded");
    }

    @Test
    void respectsSpeedMultiplier() {
        var clock = new AtomicLong(0);
        var selector = new DurationSelector(
                List.of(
                        new CorpusSelectorConfig.DurationPhase("normal", Duration.ofSeconds(10)),
                        new CorpusSelectorConfig.DurationPhase("degraded", null)),
                () -> 10.0,
                clock::get);

        clock.set(Duration.ofSeconds(2).toNanos());
        assertThat(selector.activePhase()).isEqualTo("degraded");
    }

    @Test
    void multiplePhases() {
        var clock = new AtomicLong(0);
        var selector = new DurationSelector(
                List.of(
                        new CorpusSelectorConfig.DurationPhase("a", Duration.ofSeconds(5)),
                        new CorpusSelectorConfig.DurationPhase("b", Duration.ofSeconds(5)),
                        new CorpusSelectorConfig.DurationPhase("c", null)),
                SpeedMultiplier.identity(),
                clock::get);

        assertThat(selector.activePhase()).isEqualTo("a");
        clock.set(Duration.ofSeconds(6).toNanos());
        assertThat(selector.activePhase()).isEqualTo("b");
        clock.set(Duration.ofSeconds(11).toNanos());
        assertThat(selector.activePhase()).isEqualTo("c");
    }

    @Test
    void terminalPhaseStaysIndefinitely() {
        var clock = new AtomicLong(0);
        var selector = new DurationSelector(
                List.of(
                        new CorpusSelectorConfig.DurationPhase("a", Duration.ofSeconds(1)),
                        new CorpusSelectorConfig.DurationPhase("b", null)),
                SpeedMultiplier.identity(),
                clock::get);

        clock.set(Duration.ofHours(1).toNanos());
        assertThat(selector.activePhase()).isEqualTo("b");
    }

    @Test
    void exactBoundaryAdvances() {
        var clock = new AtomicLong(0);
        var selector = new DurationSelector(
                List.of(
                        new CorpusSelectorConfig.DurationPhase("a", Duration.ofSeconds(5)),
                        new CorpusSelectorConfig.DurationPhase("b", null)),
                SpeedMultiplier.identity(),
                clock::get);

        clock.set(Duration.ofSeconds(5).toNanos());
        assertThat(selector.activePhase()).isEqualTo("b");
    }
}
