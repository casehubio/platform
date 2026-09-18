package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.NoOpSimulationCorpus;
import io.casehub.platform.simulation.SimulationExhaustedException;
import org.junit.jupiter.api.Test;

import static io.casehub.platform.simulation.strategy.ListBackedCorpus.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SequentialStrategyTest {

    private static final String QN = "test-spi.method";

    // --- WRAP mode ---

    @Test
    void wrapModeReturnsEntriesInOrder() {
        final var corpus = ListBackedCorpus.of(
                entry("k1", "in1", "out1"),
                entry("k2", "in2", "out2"),
                entry("k3", "in3", "out3"));

        final var strategy = new SequentialStrategy<String, String>(corpus, QN, ExhaustionPolicy.WRAP);

        assertThat(strategy.resolve("any")).isEqualTo("out1");
        assertThat(strategy.resolve("any")).isEqualTo("out2");
        assertThat(strategy.resolve("any")).isEqualTo("out3");
    }

    @Test
    void wrapModeWrapsAroundAtEnd() {
        final var corpus = ListBackedCorpus.of(
                entry("k1", "in1", "A"),
                entry("k2", "in2", "B"));

        final var strategy = new SequentialStrategy<String, String>(corpus, QN, ExhaustionPolicy.WRAP);

        assertThat(strategy.resolve("x")).isEqualTo("A");
        assertThat(strategy.resolve("x")).isEqualTo("B");
        assertThat(strategy.resolve("x")).isEqualTo("A");
        assertThat(strategy.resolve("x")).isEqualTo("B");
        assertThat(strategy.resolve("x")).isEqualTo("A");
    }

    @Test
    void wrapModeCanResolveReturnsTrueWhenCorpusNonEmpty() {
        final var corpus = ListBackedCorpus.of(entry("k1", "in", "out"));
        final var strategy = new SequentialStrategy<String, String>(corpus, QN, ExhaustionPolicy.WRAP);

        assertThat(strategy.canResolve("any")).isTrue();
    }

    // --- THROW mode ---

    @Test
    void throwModeReturnsEntriesInOrder() {
        final var corpus = ListBackedCorpus.of(
                entry("k1", "in1", "out1"),
                entry("k2", "in2", "out2"));

        final var strategy = new SequentialStrategy<String, String>(corpus, QN, ExhaustionPolicy.THROW);

        assertThat(strategy.resolve("any")).isEqualTo("out1");
        assertThat(strategy.resolve("any")).isEqualTo("out2");
    }

    @Test
    void throwModeThrowsOnExhaustion() {
        final var corpus = ListBackedCorpus.of(entry("k1", "in1", "out1"));
        final var strategy = new SequentialStrategy<String, String>(corpus, QN, ExhaustionPolicy.THROW);

        strategy.resolve("any");

        assertThatThrownBy(() -> strategy.resolve("any"))
                .isInstanceOf(SimulationExhaustedException.class)
                .hasMessageContaining("exhausted");
    }

    @Test
    void throwModeCanResolveReturnsFalseWhenExhausted() {
        final var corpus = ListBackedCorpus.of(entry("k1", "in", "out"));
        final var strategy = new SequentialStrategy<String, String>(corpus, QN, ExhaustionPolicy.THROW);

        assertThat(strategy.canResolve("any")).isTrue();
        strategy.resolve("any");
        assertThat(strategy.canResolve("any")).isFalse();
    }

    // --- Empty corpus ---

    @Test
    void canResolveReturnsFalseOnEmptyCorpus() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var strategy = new SequentialStrategy<>(corpus, QN, ExhaustionPolicy.WRAP);

        assertThat(strategy.canResolve("any")).isFalse();
    }

    @Test
    void resolveOnEmptyCorpusThrows() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var strategy = new SequentialStrategy<>(corpus, QN, ExhaustionPolicy.WRAP);

        assertThatThrownBy(() -> strategy.resolve("any"))
                .isInstanceOf(SimulationExhaustedException.class);
    }
}
