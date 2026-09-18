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


// --- profile parsing ---

    @Test
    void parsesProfileEntries() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.notification-store.store.strategy", "sequential")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.profileNames()).containsExactly("ci-replay");
    }

    @Test
    void parsesProfileCorpusFiles() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.corpus.files", "fixtures/captured.yaml")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.profileNames()).containsExactly("ci-replay");
    }

    @Test
    void profileDoesNotAffectFlatConfig() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.agent-provider.invoke.strategy", "key-lookup")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.strategyFor("agent-provider.invoke")).hasValue("key-lookup");
    }

    @Test
    void activeProfileOverridesFlatConfig() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.agent-provider.invoke.strategy", "key-lookup")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .withDefaultValue("casehub.simulation.active-profile", "ci-replay")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.strategyFor("agent-provider.invoke")).hasValue("recorded-replay");
    }

    @Test
    void activeProfileFallsBackToFlatForUnconfiguredMethods() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.case-memory-store.query.capture", "true")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .withDefaultValue("casehub.simulation.active-profile", "ci-replay")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.strategyFor("agent-provider.invoke")).hasValue("recorded-replay");
        assertThat(simConfig.captureEnabled("case-memory-store.query")).isTrue();
    }

    @Test
    void resolveProfileReturnsSimulationProfile() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.agent-provider.invoke.strategy", "key-lookup")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        var profile = simConfig.resolve("ci-replay");
        assertThat(profile).isPresent();
        assertThat(profile.get().config().strategyFor("agent-provider.invoke"))
                .hasValue("recorded-replay");
    }

    @Test
    void resolveProfileFallsBackToFlatConfig() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.agent-provider.invoke.strategy", "key-lookup")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.notification-store.store.strategy", "sequential")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        var profile = simConfig.resolve("ci-replay");
        assertThat(profile).isPresent();
        assertThat(profile.get().config().strategyFor("notification-store.store"))
                .hasValue("sequential");
        assertThat(profile.get().config().strategyFor("agent-provider.invoke"))
                .hasValue("key-lookup");
    }

    @Test
    void resolveUnknownProfileReturnsEmpty() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.resolve("nonexistent")).isEmpty();
    }

    @Test
    void multipleProfilesParsedIndependently() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .withDefaultValue("casehub.simulation.profiles.dev-demo.agent-provider.invoke.strategy", "sequential")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.profileNames()).containsExactlyInAnyOrder("ci-replay", "dev-demo");

        var ciProfile = simConfig.resolve("ci-replay");
        assertThat(ciProfile.get().config().strategyFor("agent-provider.invoke"))
                .hasValue("recorded-replay");

        var devProfile = simConfig.resolve("dev-demo");
        assertThat(devProfile.get().config().strategyFor("agent-provider.invoke"))
                .hasValue("sequential");
    }

    @Test
    void activeProfileCorpusFilesReturnedWhenConfigured() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.corpus.files", "fixtures/captured.yaml")
                             .withDefaultValue("casehub.simulation.profiles.ci-replay.agent-provider.invoke.strategy", "recorded-replay")
                             .withDefaultValue("casehub.simulation.active-profile", "ci-replay")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.activeProfileCorpusFiles())
                .isPresent()
                .hasValueSatisfying(files -> assertThat(files).containsExactly("fixtures/captured.yaml"));
    }

    @Test
    void activeProfileCorpusFilesEmptyWhenNoActiveProfile() {
        var config = new SmallRyeConfigBuilder()
                             .withDefaultValue("casehub.simulation.agent-provider.invoke.strategy", "key-lookup")
                             .build();
        var simConfig = new SmallRyeSimulationConfig(config);

        assertThat(simConfig.activeProfileCorpusFiles()).isEmpty();
    }

    private SmallRyeConfig buildConfig(String key, String value) {
        return new SmallRyeConfigBuilder()
                .withDefaultValue(key, value)
                .build();
    }
}
