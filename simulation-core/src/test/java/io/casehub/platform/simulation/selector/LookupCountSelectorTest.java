package io.casehub.platform.simulation.selector;

import io.casehub.platform.simulation.CorpusSelectorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LookupCountSelectorTest {

    @Test
    void firstPhaseActiveBeforeCountReached() {
        var selector = new LookupCountSelector(List.of(
                new CorpusSelectorConfig.CountPhase("pending", 3),
                new CorpusSelectorConfig.CountPhase("completed", 0)));

        assertThat(selector.activePhase()).isEqualTo("pending");
        selector.onResolved();
        assertThat(selector.activePhase()).isEqualTo("pending");
        selector.onResolved();
        assertThat(selector.activePhase()).isEqualTo("pending");
    }

    @Test
    void advancesToNextPhaseAfterCount() {
        var selector = new LookupCountSelector(List.of(
                new CorpusSelectorConfig.CountPhase("pending", 2),
                new CorpusSelectorConfig.CountPhase("completed", 0)));

        selector.onResolved();
        selector.onResolved();
        assertThat(selector.activePhase()).isEqualTo("completed");
    }

    @Test
    void terminalPhaseStaysIndefinitely() {
        var selector = new LookupCountSelector(List.of(
                new CorpusSelectorConfig.CountPhase("a", 1),
                new CorpusSelectorConfig.CountPhase("b", 0)));

        selector.onResolved();
        assertThat(selector.activePhase()).isEqualTo("b");
        selector.onResolved();
        selector.onResolved();
        assertThat(selector.activePhase()).isEqualTo("b");
    }

    @Test
    void threePhases() {
        var selector = new LookupCountSelector(List.of(
                new CorpusSelectorConfig.CountPhase("first", 2),
                new CorpusSelectorConfig.CountPhase("second", 3),
                new CorpusSelectorConfig.CountPhase("third", 0)));

        assertThat(selector.activePhase()).isEqualTo("first");
        selector.onResolved();
        selector.onResolved();
        assertThat(selector.activePhase()).isEqualTo("second");
        selector.onResolved();
        selector.onResolved();
        selector.onResolved();
        assertThat(selector.activePhase()).isEqualTo("third");
    }

    @Test
    void activePhaseDoesNotIncrementCounter() {
        var selector = new LookupCountSelector(List.of(
                new CorpusSelectorConfig.CountPhase("a", 1),
                new CorpusSelectorConfig.CountPhase("b", 0)));

        selector.activePhase();
        selector.activePhase();
        selector.activePhase();
        assertThat(selector.activePhase()).isEqualTo("a");
    }
}
