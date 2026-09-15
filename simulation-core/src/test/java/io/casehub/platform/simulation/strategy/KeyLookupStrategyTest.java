package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.SimulationKeyNotFoundException;
import org.junit.jupiter.api.Test;

import static io.casehub.platform.simulation.strategy.ListBackedCorpus.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyLookupStrategyTest {

    private static final String QN = "test-spi.method";

    @Test
    void exactKeyMatchReturnsOutput() {
        final var corpus = ListBackedCorpus.of(
                entry("alpha", "in1", "result-alpha"),
                entry("beta", "in2", "result-beta"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> input);

        assertThat(strategy.resolve("alpha")).isEqualTo("result-alpha");
        assertThat(strategy.resolve("beta")).isEqualTo("result-beta");
    }

    @Test
    void keyMissThrowsSimulationKeyNotFoundException() {
        final var corpus = ListBackedCorpus.of(entry("alpha", "in", "out"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> input);

        assertThatThrownBy(() -> strategy.resolve("missing"))
                .isInstanceOf(SimulationKeyNotFoundException.class)
                .satisfies(ex -> assertThat(((SimulationKeyNotFoundException) ex).getKey())
                        .isEqualTo("missing"));
    }

    @Test
    void canResolveReturnsTrueForExistingKey() {
        final var corpus = ListBackedCorpus.of(entry("alpha", "in", "out"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> input);

        assertThat(strategy.canResolve("alpha")).isTrue();
    }

    @Test
    void canResolveReturnsFalseForMissingKey() {
        final var corpus = ListBackedCorpus.of(entry("alpha", "in", "out"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> input);

        assertThat(strategy.canResolve("missing")).isFalse();
    }

    @Test
    void customKeyExtractorTransformsInput() {
        final var corpus = ListBackedCorpus.of(
                entry("HELLO", "in", "uppercase-match"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, String::toUpperCase);

        assertThat(strategy.resolve("hello")).isEqualTo("uppercase-match");
    }
}
