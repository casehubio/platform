package io.casehub.platform.simulation.strategy;

import io.casehub.platform.simulation.SimilarityScorer;
import io.casehub.platform.simulation.SimulationNoMatchException;
import org.junit.jupiter.api.Test;

import static io.casehub.platform.simulation.strategy.ListBackedCorpus.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NearestMatchStrategyTest {

    private static final String QN = "test-spi.method";

    @Test
    void bestMatchAboveThresholdIsReturned() {
        var corpus = ListBackedCorpus.of(
                entry("k1", "alpha", "result-alpha"),
                entry("k2", "beta", "result-beta"),
                entry("k3", "alphabet", "result-alphabet"));

        SimilarityScorer<String> scorer = (q, c) -> {
            if (q.equals(c)) return 1.0;
            if (q.startsWith(c) || c.startsWith(q)) return 0.7;
            return 0.0;
        };

        var strategy = new NearestMatchStrategy<>(corpus, QN, scorer, 0.5);

        assertThat(strategy.resolve("alpha")).isEqualTo("result-alpha");
    }

    @Test
    void higherScoreWinsOverLowerScore() {
        var corpus = ListBackedCorpus.of(
                entry("k1", "abc", "result-abc"),
                entry("k2", "abcdef", "result-abcdef"));

        SimilarityScorer<String> scorer = (q, c) -> {
            int common = 0;
            for (int i = 0; i < Math.min(q.length(), c.length()); i++) {
                if (q.charAt(i) == c.charAt(i)) common++;
            }
            return (double) common / Math.max(q.length(), c.length());
        };

        var strategy = new NearestMatchStrategy<>(corpus, QN, scorer, 0.0);

        assertThat(strategy.resolve("abcde")).isEqualTo("result-abcdef");
    }

    @Test
    void noMatchAboveThresholdThrowsException() {
        var corpus = ListBackedCorpus.of(
                entry("k1", "alpha", "result-alpha"));

        SimilarityScorer<String> scorer = (q, c) -> 0.1;

        var strategy = new NearestMatchStrategy<>(corpus, QN, scorer, 0.5);

        assertThatThrownBy(() -> strategy.resolve("unrelated"))
                .isInstanceOf(SimulationNoMatchException.class);
    }

    @Test
    void canResolveReturnsTrueWhenMatchAboveThreshold() {
        var corpus = ListBackedCorpus.of(
                entry("k1", "alpha", "result-alpha"));

        SimilarityScorer<String> scorer = (q, c) -> 0.9;

        var strategy = new NearestMatchStrategy<>(corpus, QN, scorer, 0.5);

        assertThat(strategy.canResolve("anything")).isTrue();
    }

    @Test
    void canResolveReturnsFalseWhenNoMatchAboveThreshold() {
        var corpus = ListBackedCorpus.of(
                entry("k1", "alpha", "result-alpha"));

        SimilarityScorer<String> scorer = (q, c) -> 0.1;

        var strategy = new NearestMatchStrategy<>(corpus, QN, scorer, 0.5);

        assertThat(strategy.canResolve("anything")).isFalse();
    }

    @Test
    void emptyCorpusCannotResolve() {
        var corpus = ListBackedCorpus.<String, String>of();

        SimilarityScorer<String> scorer = (q, c) -> 1.0;

        var strategy = new NearestMatchStrategy<>(corpus, QN, scorer, 0.0);

        assertThat(strategy.canResolve("anything")).isFalse();
    }
}
