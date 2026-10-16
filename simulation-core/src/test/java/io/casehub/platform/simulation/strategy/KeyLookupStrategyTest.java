package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.DataRealism;
import io.casehub.platform.simulation.ExhaustionPolicy;
import io.casehub.platform.simulation.MatchPolicy;
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

    @Test
    void dataRealismReturnsDomainPlausible() {
        var corpus   = ListBackedCorpus.of(entry("k1", "in", "out"));
        var strategy = new KeyLookupStrategy<String, String>(corpus, QN, input -> input);
        assertThat(strategy.dataRealism()).isEqualTo(DataRealism.DOMAIN_PLAUSIBLE);
    }


    @Test
    void sequentialMatchIteratesThroughRecordsInOrder() {
        final var corpus = ListBackedCorpus.of(
                entry("conv", "in1", "first"),
                entry("conv", "in2", "second"),
                entry("conv", "in3", "third"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> "conv",
                MatchPolicy.SEQUENTIAL, ExhaustionPolicy.CLAMP);

        assertThat(strategy.resolve("any")).isEqualTo("first");
        assertThat(strategy.resolve("any")).isEqualTo("second");
        assertThat(strategy.resolve("any")).isEqualTo("third");
    }

    @Test
    void sequentialClampRepeatsLastRecord() {
        final var corpus = ListBackedCorpus.of(
                entry("conv", "in1", "first"),
                entry("conv", "in2", "last"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> "conv",
                MatchPolicy.SEQUENTIAL, ExhaustionPolicy.CLAMP);

        strategy.resolve("any");
        strategy.resolve("any");
        assertThat(strategy.resolve("any")).isEqualTo("last");
        assertThat(strategy.resolve("any")).isEqualTo("last");
    }

    @Test
    void sequentialWrapCyclesBackToFirst() {
        final var corpus = ListBackedCorpus.of(
                entry("conv", "in1", "first"),
                entry("conv", "in2", "second"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> "conv",
                MatchPolicy.SEQUENTIAL, ExhaustionPolicy.WRAP);

        assertThat(strategy.resolve("any")).isEqualTo("first");
        assertThat(strategy.resolve("any")).isEqualTo("second");
        assertThat(strategy.resolve("any")).isEqualTo("first");
    }

    @Test
    void sequentialThrowThrowsWhenExhausted() {
        final var corpus = ListBackedCorpus.of(
                entry("conv", "in1", "only"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> "conv",
                MatchPolicy.SEQUENTIAL, ExhaustionPolicy.THROW);

        assertThat(strategy.resolve("any")).isEqualTo("only");
        assertThatThrownBy(() -> strategy.resolve("any"))
                .isInstanceOf(SimulationKeyNotFoundException.class);
    }

    @Test
    void randomMatchReturnsFromMatchingRecords() {
        final var corpus = ListBackedCorpus.of(
                entry("conv", "in1", "a"),
                entry("conv", "in2", "b"),
                entry("other", "in3", "c"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> "conv",
                MatchPolicy.RANDOM, ExhaustionPolicy.CLAMP);

        var results = new java.util.HashSet<String>();
        for (int i = 0; i < 100; i++) {
            results.add(strategy.resolve("any"));
        }
        assertThat(results).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void firstMatchWithMultipleRecordsAlwaysReturnsFirst() {
        final var corpus = ListBackedCorpus.of(
                entry("conv", "in1", "first"),
                entry("conv", "in2", "second"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> "conv",
                MatchPolicy.FIRST, ExhaustionPolicy.CLAMP);

        assertThat(strategy.resolve("any")).isEqualTo("first");
        assertThat(strategy.resolve("any")).isEqualTo("first");
    }

    @Test
    void sequentialMaintainsIndependentCursorsPerKey() {
        final var corpus = ListBackedCorpus.of(
                entry("a", "in1", "a-first"),
                entry("a", "in2", "a-second"),
                entry("b", "in3", "b-first"),
                entry("b", "in4", "b-second"));

        final var strategy = new KeyLookupStrategy<String, String>(
                corpus, QN, input -> input,
                MatchPolicy.SEQUENTIAL, ExhaustionPolicy.CLAMP);

        assertThat(strategy.resolve("a")).isEqualTo("a-first");
        assertThat(strategy.resolve("b")).isEqualTo("b-first");
        assertThat(strategy.resolve("a")).isEqualTo("a-second");
        assertThat(strategy.resolve("b")).isEqualTo("b-second");
    }

}
