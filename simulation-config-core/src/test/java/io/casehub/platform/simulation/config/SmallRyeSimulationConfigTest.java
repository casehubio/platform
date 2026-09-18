package io.casehub.platform.simulation.config;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmallRyeSimulationConfigTest {

    @Test
    void parsesStrategyFromProperties() {
        var config = buildConfig(
                "casehub.simulation.my-spi.query.strategy", "sequential");
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.strategyFor("my-spi.query")).hasValue("sequential");
    }

    @Test
    void parsesCaptureFromProperties() {
        var config = buildConfig(
                "casehub.simulation.my-spi.store.capture", "true");
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.captureEnabled("my-spi.store")).isTrue();
    }

    @Test
    void parsesExhaustionPolicyFromProperties() {
        var config = buildConfig(
                "casehub.simulation.my-spi.query.exhaustion-policy", "THROW");
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.exhaustionPolicy("my-spi.query"))
                .hasValue(ExhaustionPolicy.THROW);
    }

    @Test
    void returnsEmptyForUnconfiguredMethod() {
        var config = buildConfig(
                "casehub.simulation.my-spi.query.strategy", "sequential");
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.strategyFor("my-spi.unknown")).isEmpty();
        assertThat(simConfig.captureEnabled("my-spi.unknown")).isFalse();
        assertThat(simConfig.exhaustionPolicy("my-spi.unknown")).isEmpty();
    }

    @Test
    void ignoresReservedKeysWithFewerThanThreeSegments() {
        var config = buildConfig(
                "casehub.simulation.corpus.files", "classpath:test.yaml");
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.strategyFor("corpus.files")).isEmpty();
    }

    @Test
    void parsesMultipleMethodsAcrossSpis() {
        var config = new SmallRyeConfigBuilder()
                .withDefaultValue("casehub.simulation.spi-a.query.strategy", "key-lookup")
                .withDefaultValue("casehub.simulation.spi-a.store.capture", "true")
                .withDefaultValue("casehub.simulation.spi-b.invoke.strategy", "sequential")
                .withDefaultValue("casehub.simulation.spi-b.invoke.exhaustion-policy", "WRAP")
                .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.strategyFor("spi-a.query")).hasValue("key-lookup");
        assertThat(simConfig.captureEnabled("spi-a.store")).isTrue();
        assertThat(simConfig.strategyFor("spi-b.invoke")).hasValue("sequential");
        assertThat(simConfig.exhaustionPolicy("spi-b.invoke"))
                .hasValue(ExhaustionPolicy.WRAP);
    }

    @Test
    void parsesKeyExtractorSpec() {
        var config = buildConfig(
                "casehub.simulation.my-spi.query.key-extractor", "field:domain");
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.extractorSpecs()).containsEntry("my-spi.query", "field:domain");
    }

    private SmallRyeConfig buildConfig(String key, String value) {
        return new SmallRyeConfigBuilder()
                .withDefaultValue(key, value)
                .build();
    }
}
