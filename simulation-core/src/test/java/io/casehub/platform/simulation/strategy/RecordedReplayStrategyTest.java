package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.NoOpSimulationCorpus;
import io.casehub.platform.simulation.SimulationExhaustedException;
import org.junit.jupiter.api.Test;

import static io.casehub.platform.simulation.strategy.ListBackedCorpus.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordedReplayStrategyTest {

    private static final String QN = "test-spi.method";

    @Test
    void keyMatchReturnsDeterministicResult() {
        final var corpus = ListBackedCorpus.of(
                entry("key-a", "in1", "result-a"),
                entry("key-b", "in2", "result-b"));

        final var strategy = new RecordedReplayStrategy<String, String>(
                corpus, QN, input -> input);

        assertThat(strategy.resolve("key-a")).isEqualTo("result-a");
        assertThat(strategy.resolve("key-b")).isEqualTo("result-b");
        assertThat(strategy.resolve("key-a")).isEqualTo("result-a");
    }

    @Test
    void fallsBackToSequentialWhenKeyNotFound() {
        final var corpus = ListBackedCorpus.of(
                entry("known", "in1", "first"),
                entry("also-known", "in2", "second"));

        final var strategy = new RecordedReplayStrategy<String, String>(
                corpus, QN, input -> input);

        assertThat(strategy.resolve("unknown-1")).isEqualTo("first");
        assertThat(strategy.resolve("unknown-2")).isEqualTo("second");
    }

    @Test
    void fallbackIndexAdvancesIndependentlyOfKeyMatches() {
        final var corpus = ListBackedCorpus.of(
                entry("fallback-0", "in1", "seq-0"),
                entry("key-a", "in2", "matched"),
                entry("fallback-2", "in3", "seq-2"));

        final var strategy = new RecordedReplayStrategy<String, String>(
                corpus, QN, input -> input);

        assertThat(strategy.resolve("key-a")).isEqualTo("matched");
        assertThat(strategy.resolve("no-match-1")).isEqualTo("seq-0");
        assertThat(strategy.resolve("key-a")).isEqualTo("matched");
        assertThat(strategy.resolve("no-match-2")).isEqualTo("matched");
        assertThat(strategy.resolve("no-match-3")).isEqualTo("seq-2");
    }

    @Test
    void canResolveReturnsTrueWhenCorpusNonEmpty() {
        final var corpus = ListBackedCorpus.of(entry("k", "in", "out"));
        final var strategy = new RecordedReplayStrategy<String, String>(
                corpus, QN, input -> input);

        assertThat(strategy.canResolve("any")).isTrue();
    }

    @Test
    void canResolveReturnsFalseOnEmptyCorpus() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var strategy = new RecordedReplayStrategy<>(corpus, QN, input -> input);

        assertThat(strategy.canResolve("any")).isFalse();
    }

    @Test
    void fallbackOnEmptyCorpusThrows() {
        final var corpus = new NoOpSimulationCorpus<String, String>();
        final var strategy = new RecordedReplayStrategy<>(corpus, QN, input -> input);

        assertThatThrownBy(() -> strategy.resolve("any"))
                .isInstanceOf(SimulationExhaustedException.class);
    }
}
