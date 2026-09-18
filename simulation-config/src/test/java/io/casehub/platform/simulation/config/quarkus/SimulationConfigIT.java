package io.casehub.platform.simulation.config.quarkus;

import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.SimulationStrategy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class SimulationConfigIT {

    @Inject
    SimulationConfig config;

    @Inject
    SimulationRuntime runtime;

    @Test
    void configBindsStrategyFromProperties() {
        assertThat(config.strategyFor("test-spi.query")).hasValue("sequential");
    }

    @Test
    void configBindsCaptureFromProperties() {
        assertThat(config.captureEnabled("test-spi.store")).isTrue();
    }

    @Test
    void runtimeResolvesSequentialStrategy() {
        Optional<SimulationStrategy<Object, Object>> strategy =
                runtime.strategyFor("test-spi.query");

        assertThat(strategy).isPresent();
        assertThat(strategy.get().resolve("any")).isEqualTo("response-1");
        assertThat(strategy.get().resolve("any")).isEqualTo("response-2");
    }

    @Test
    void yamlCorpusIsSeededAtStartup() {
        Optional<SimulationStrategy<Object, Object>> strategy =
                runtime.strategyFor("test-spi.query");

        assertThat(strategy).isPresent();
        assertThat(strategy.get().canResolve("any")).isTrue();
    }

    @Test
    void declarativeExtractorWorksWithKeyLookup() {
        Optional<SimulationStrategy<Object, Object>> strategy =
                runtime.strategyFor("test-spi.lookup");

        assertThat(strategy).isPresent();
        Object result = strategy.get().resolve(Map.of("name", "alice", "age", 30));
        assertThat(result).isEqualTo("found-alice");
    }
}
