package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.NoOpSimulationCorpus;
import io.casehub.platform.simulation.SimulationExhaustedException;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.casehub.platform.simulation.strategy.ListBackedCorpus.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RandomStrategyTest {

    private static final String QN = "test-spi.method";

    @Test
    void samplesFromCorpusWithSeededRandom() {
        final var corpus = ListBackedCorpus.of(
                entry("k1", "in1", "A"),
                entry("k2", "in2", "B"),
                entry("k3", "in3", "C"));

        final var strategy = new RandomStrategy<String, String>(corpus, QN, new Random(42), null);

        final String first = strategy.resolve("any");
        assertThat(first).isIn("A", "B", "C");
    }

    @Test
    void sameSeedProducesSameSequence() {
        final var corpus = ListBackedCorpus.of(
                entry("k1", "in1", "A"),
                entry("k2", "in2", "B"),
                entry("k3", "in3", "C"),
                entry("k4", "in4", "D"),
                entry("k5", "in5", "E"));

        final var strategy1 = new RandomStrategy<String, String>(corpus, QN, new Random(99), null);
        final var strategy2 = new RandomStrategy<String, String>(corpus, QN, new Random(99), null);

        for (int i = 0; i < 10; i++) {
            assertThat(strategy1.resolve("x")).isEqualTo(strategy2.resolve("x"));
        }
    }

    @Test
    void generatorModeIgnoresCorpus() {
        final var corpus = ListBackedCorpus.of(entry("k1", "in", "corpus-value"));
        final var strategy = new RandomStrategy<String, String>(
                corpus, QN, new Random(), () -> "generated-value");

        assertThat(strategy.resolve("any")).isEqualTo("generated-value");
        assertThat(strategy.resolve("any")).isEqualTo("generated-value");
    }

    @Test
    void canResolveReturnsTrueWhenCorpusNonEmpty() {
        final var corpus = ListBackedCorpus.of(entry("k1", "in", "out"));
        final var strategy = new RandomStrategy<String, String>(corpus, QN, new Random(), null);

        assertThat(strategy.canResolve("any")).isTrue();
    }

    @Test
    void canResolveReturnsTrueWhenGeneratorProvided() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var strategy = new RandomStrategy<>(corpus, QN, new Random(), () -> "val");

        assertThat(strategy.canResolve("any")).isTrue();
    }

    @Test
    void canResolveReturnsFalseOnEmptyCorpusWithoutGenerator() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var strategy = new RandomStrategy<>(corpus, QN, new Random(), null);

        assertThat(strategy.canResolve("any")).isFalse();
    }

    @Test
    void resolveOnEmptyCorpusWithoutGeneratorThrows() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var strategy = new RandomStrategy<>(corpus, QN, new Random(), null);

        assertThatThrownBy(() -> strategy.resolve("any"))
                .isInstanceOf(SimulationExhaustedException.class);
    }
}
