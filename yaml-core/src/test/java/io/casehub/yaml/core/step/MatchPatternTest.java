package io.casehub.yaml.core.step;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MatchPatternTest {

    @Test
    void valuePatternMatchesEqualScalar() {
        var pattern = new MatchPattern.ValuePattern("ACTIVE");
        assertThat(pattern.matches("ACTIVE")).isTrue();
        assertThat(pattern.matches("SUSPENDED")).isFalse();
    }

    @Test
    void valuePatternMatchesNull() {
        var pattern = new MatchPattern.ValuePattern(null);
        assertThat(pattern.matches(null)).isTrue();
        assertThat(pattern.matches("x")).isFalse();
    }

    @Test
    void valuePatternMatchesInteger() {
        var pattern = new MatchPattern.ValuePattern(42);
        assertThat(pattern.matches(42)).isTrue();
        assertThat(pattern.matches(43)).isFalse();
    }

    @Test
    void structuralPatternMatchesSubset() {
        var pattern = new MatchPattern.StructuralPattern(
                Map.of("type", "trade"));
        assertThat(pattern.matches(
                Map.of("type", "trade", "amount", 1000))).isTrue();
        assertThat(pattern.matches(
                Map.of("type", "settlement"))).isFalse();
    }

    @Test
    void structuralPatternRejectsNonMap() {
        var pattern = new MatchPattern.StructuralPattern(
                Map.of("type", "trade"));
        assertThat(pattern.matches("not a map")).isFalse();
        assertThat(pattern.matches(null)).isFalse();
    }

    @Test
    void structuralPatternEmptyMatchesAnyMap() {
        var pattern = new MatchPattern.StructuralPattern(Map.of());
        assertThat(pattern.matches(Map.of("a", 1))).isTrue();
        assertThat(pattern.matches(Map.of())).isTrue();
    }

    @Test
    void structuralPatternNestedExactEquality() {
        var pattern = new MatchPattern.StructuralPattern(
                Map.of("meta", Map.of("version", 2)));
        assertThat(pattern.matches(
                Map.of("meta", Map.of("version", 2)))).isTrue();
        assertThat(pattern.matches(
                Map.of("meta", Map.of("version", 2, "extra", true)))).isFalse();
    }

    @Test
    void structuralPatternMissingFieldDoesNotMatch() {
        var pattern = new MatchPattern.StructuralPattern(
                Map.of("type", "trade", "priority", "HIGH"));
        assertThat(pattern.matches(
                Map.of("type", "trade"))).isFalse();
    }

    @Test
    void defaultPatternMatchesEverything() {
        var pattern = new MatchPattern.DefaultPattern();
        assertThat(pattern.matches("anything")).isTrue();
        assertThat(pattern.matches(null)).isTrue();
        assertThat(pattern.matches(Map.of())).isTrue();
        assertThat(pattern.matches(42)).isTrue();
    }

    @Test
    void matchCaseDefensiveCopy() {
        var steps = new java.util.ArrayList<>(List.of(Map.<String, Object>of("action", "do")));
        var mc = new MatchCase(new MatchPattern.ValuePattern("x"), null, steps);
        steps.clear();
        assertThat(mc.steps()).hasSize(1);
    }

    @Test
    void sealedInterfacePermitsAllVariants() {
        MatchPattern v = new MatchPattern.ValuePattern("a");
        MatchPattern s = new MatchPattern.StructuralPattern(Map.of());
        MatchPattern d = new MatchPattern.DefaultPattern();
        assertThat(v).isInstanceOf(MatchPattern.class);
        assertThat(s).isInstanceOf(MatchPattern.class);
        assertThat(d).isInstanceOf(MatchPattern.class);
    }
}
