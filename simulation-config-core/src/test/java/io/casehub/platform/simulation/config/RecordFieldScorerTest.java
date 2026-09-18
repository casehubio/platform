package io.casehub.platform.simulation.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RecordFieldScorerTest {

    @Test
    void singleExactFieldScoresCorrectly() {
        var scorer = RecordFieldScorer.<Map<String, Object>>builder()
                .field("domain", FieldSimilarity.EXACT, 1.0)
                .build();

        double match = scorer.score(
                Map.of("domain", "test"),
                Map.of("domain", "test"));
        double miss = scorer.score(
                Map.of("domain", "test"),
                Map.of("domain", "other"));

        assertThat(match).isEqualTo(1.0);
        assertThat(miss).isEqualTo(0.0);
    }

    @Test
    void weightedMultiFieldScoring() {
        var scorer = RecordFieldScorer.<Map<String, Object>>builder()
                .field("domain", FieldSimilarity.EXACT, 1.0)
                .field("question", FieldSimilarity.EXACT, 0.5)
                .build();

        double score = scorer.score(
                Map.of("domain", "test", "question", "how?"),
                Map.of("domain", "test", "question", "what?"));

        assertThat(score).isCloseTo(0.667, within(0.01));
    }

    @Test
    void missingFieldScoresZero() {
        var scorer = RecordFieldScorer.<Map<String, Object>>builder()
                .field("domain", FieldSimilarity.EXACT, 1.0)
                .build();

        double score = scorer.score(
                Map.of("domain", "test"),
                Map.of("other", "value"));

        assertThat(score).isEqualTo(0.0);
    }

    @Test
    void worksWithRecordTypes() {
        record QueryInput(String domain, int limit) {}

        var scorer = RecordFieldScorer.<QueryInput>builder()
                .field("domain", FieldSimilarity.EXACT, 1.0)
                .field("limit", FieldSimilarity.IGNORE, 0.0)
                .build();

        double score = scorer.score(
                new QueryInput("test", 10),
                new QueryInput("test", 50));

        assertThat(score).isEqualTo(1.0);
    }
}
