package io.casehub.platform.simulation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MapSimulationConfigTest {

    @Test
    void strategyForReturnsConfiguredStrategy() {
        var config = MapSimulationConfig.of(Map.of("spi.query", "sequential"));
        assertThat(config.strategyFor("spi.query")).hasValue("sequential");
    }

    @Test
    void strategyForReturnsEmptyForUnconfigured() {
        var config = MapSimulationConfig.of(Map.of("spi.query", "sequential"));
        assertThat(config.strategyFor("spi.store")).isEmpty();
    }

    @Test
    void captureEnabledFromExplicitConfig() {
        var config = MapSimulationConfig.of(
                Map.of("spi.query", "sequential"),
                Map.of("spi.store", true));
        assertThat(config.captureEnabled("spi.store")).isTrue();
        assertThat(config.captureEnabled("spi.query")).isFalse();
    }

    @Test
    void exhaustionPolicyAlwaysEmpty() {
        var config = MapSimulationConfig.of(Map.of("spi.query", "sequential"));
        assertThat(config.exhaustionPolicy("spi.query")).isEmpty();
    }

    @Test
    void strategiesReturnsDefensiveCopy() {
        var config = MapSimulationConfig.of(Map.of("spi.query", "sequential"));
        assertThat(config.strategies()).containsEntry("spi.query", "sequential");
    }

    @Test
    void emptyMapReturnsEmptyForAll() {
        var config = MapSimulationConfig.of(Map.of());
        assertThat(config.strategyFor("anything")).isEmpty();
        assertThat(config.captureEnabled("anything")).isFalse();
    }
}
