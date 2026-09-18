package io.casehub.platform.simulation;

import io.casehub.platform.simulation.inmem.InMemorySimulationCorpus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationOverlayTest {

    @Test
    void overlayExposesConfigCorpusAndJournal() {
        var config = MapSimulationConfig.of(Map.of("spi.query", "sequential"));
        var corpus = new InMemorySimulationCorpus<>();
        var overlay = new SimulationOverlay(config, corpus);

        assertThat(overlay.config().strategyFor("spi.query")).hasValue("sequential");
        assertThat(overlay.corpus()).isSameAs(corpus);
        assertThat(overlay.journal()).isNotNull();
        assertThat(overlay.journal().entries()).isEmpty();
    }

    @Test
    void overlayHasIndependentStrategyCache() {
        var overlay = new SimulationOverlay(
                MapSimulationConfig.of(Map.of("spi.query", "sequential")),
                new InMemorySimulationCorpus<>());

        assertThat(overlay.strategyCache()).isNotNull();
        assertThat(overlay.strategyCache()).isEmpty();
    }
}
