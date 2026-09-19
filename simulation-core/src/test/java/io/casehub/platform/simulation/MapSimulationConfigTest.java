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

    // --- builder ---

    @Test
    void builderStrategyAndCapture() {
        var config = MapSimulationConfig.builder()
                .strategy("spi.query", "key-lookup")
                .capture("spi.store", true)
                .build();

        assertThat(config.strategyFor("spi.query")).hasValue("key-lookup");
        assertThat(config.captureEnabled("spi.store")).isTrue();
        assertThat(config.captureEnabled("spi.query")).isFalse();
    }

    @Test
    void builderExhaustionPolicy() {
        var config = MapSimulationConfig.builder()
                .strategy("spi.query", "sequential")
                .exhaustion("spi.query", ExhaustionPolicy.THROW)
                .build();

        assertThat(config.exhaustionPolicy("spi.query")).hasValue(ExhaustionPolicy.THROW);
        assertThat(config.exhaustionPolicy("spi.store")).isEmpty();
    }

    @Test
    void builderThreshold() {
        var config = MapSimulationConfig.builder()
                .strategy("spi.query", "nearest-match")
                .threshold("spi.query", 0.85)
                .build();

        assertThat(config.threshold("spi.query")).hasValue(0.85);
        assertThat(config.threshold("spi.store")).isEmpty();
    }

    @Test
    void builderAllProperties() {
        var config = MapSimulationConfig.builder()
                .strategy("spi.query", "nearest-match")
                .threshold("spi.query", 0.7)
                .exhaustion("spi.query", ExhaustionPolicy.WRAP)
                .capture("spi.store", true)
                .build();

        assertThat(config.strategyFor("spi.query")).hasValue("nearest-match");
        assertThat(config.threshold("spi.query")).hasValue(0.7);
        assertThat(config.exhaustionPolicy("spi.query")).hasValue(ExhaustionPolicy.WRAP);
        assertThat(config.captureEnabled("spi.store")).isTrue();
    }

    @Test
    void builderEmpty() {
        var config = MapSimulationConfig.builder().build();

        assertThat(config.strategyFor("anything")).isEmpty();
        assertThat(config.captureEnabled("anything")).isFalse();
        assertThat(config.exhaustionPolicy("anything")).isEmpty();
        assertThat(config.threshold("anything")).isEmpty();
    }
}
