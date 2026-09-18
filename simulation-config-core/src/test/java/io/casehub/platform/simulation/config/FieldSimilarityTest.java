package io.casehub.platform.simulation.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FieldSimilarityTest {

    @Test
    void exactMatchScoresOneForEqual() {
        assertThat(FieldSimilarity.EXACT.score("hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void exactMatchScoresZeroForDifferent() {
        assertThat(FieldSimilarity.EXACT.score("hello", "world")).isEqualTo(0.0);
    }

    @Test
    void exactMatchHandlesNulls() {
        assertThat(FieldSimilarity.EXACT.score(null, null)).isEqualTo(1.0);
        assertThat(FieldSimilarity.EXACT.score(null, "x")).isEqualTo(0.0);
    }

    @Test
    void substringScoresOneForExactMatch() {
        assertThat(FieldSimilarity.SUBSTRING.score("hello", "hello")).isEqualTo(1.0);
    }

    @Test
    void substringScoresPartialForContainment() {
        assertThat(FieldSimilarity.SUBSTRING.score("hello world", "hello")).isEqualTo(0.8);
        assertThat(FieldSimilarity.SUBSTRING.score("hello", "hello world")).isEqualTo(0.8);
    }

    @Test
    void substringScoresZeroForNoOverlap() {
        assertThat(FieldSimilarity.SUBSTRING.score("hello", "world")).isEqualTo(0.0);
    }

    @Test
    void numericRangeScoresOneForEqual() {
        assertThat(FieldSimilarity.NUMERIC_RANGE.score(100, 100)).isEqualTo(1.0);
    }

    @Test
    void numericRangeDecaysWithDistance() {
        double score = FieldSimilarity.NUMERIC_RANGE.score(100, 150);
        assertThat(score).isGreaterThan(0.0).isLessThan(1.0);
    }

    @Test
    void numericRangeScoresZeroForNonNumeric() {
        assertThat(FieldSimilarity.NUMERIC_RANGE.score("hello", 100)).isEqualTo(0.0);
    }

    @Test
    void ignoreAlwaysScoresOne() {
        assertThat(FieldSimilarity.IGNORE.score("anything", "else")).isEqualTo(1.0);
        assertThat(FieldSimilarity.IGNORE.score(null, null)).isEqualTo(1.0);
    }
}
