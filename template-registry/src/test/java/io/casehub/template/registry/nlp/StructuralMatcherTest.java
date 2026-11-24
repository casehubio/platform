package io.casehub.template.registry.nlp;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralMatcherTest {

    private final OpenNlpTokenizer tokenizer = new OpenNlpTokenizer();

    @Test
    void exactMatchScoresHigh() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}", tokenizer);
        var tokens = tokenizer.tokenize("I approve this design");
        var result = matcher.match(tokens, Map.of());
        assertThat(result).isPresent();
        assertThat(result.get().score()).isGreaterThan(0.5);
    }

    @Test
    void tenseVariation() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}", tokenizer);
        var tokens = tokenizer.tokenize("I approved this design");
        var result = matcher.match(tokens, Map.of());
        assertThat(result).isPresent();
        assertThat(result.get().fields()).containsKey("decision");
    }

    @Test
    void enumConstraintByLemma() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}", tokenizer);
        var constraints = Map.of("decision",
            VariableConstraint.enumOf("approve", "reject"));
        var tokens = tokenizer.tokenize("I approved this design");
        var result = matcher.match(tokens, constraints);
        assertThat(result).isPresent();
        assertThat(result.get().fields().get("decision")).isEqualTo("approve");
    }

    @Test
    void enumConstraintRejects() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}", tokenizer);
        var constraints = Map.of("decision",
            VariableConstraint.enumOf("approve", "reject"));
        var tokens = tokenizer.tokenize("I celebrate this design");
        var result = matcher.match(tokens, constraints);
        assertThat(result).isEmpty();
    }

    @Test
    void noMatchForUnrelated() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}", tokenizer);
        var tokens = tokenizer.tokenize("The weather is nice today");
        var result = matcher.match(tokens, Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void clauseCapture() {
        var matcher = StructuralMatcher.compile(
            "Escalating to ${target} because ${reason}", tokenizer);
        var tokens = tokenizer.tokenize(
            "Escalating to the manager because the deadline is tomorrow");
        var result = matcher.match(tokens, Map.of());
        assertThat(result).isPresent();
        assertThat(result.get().fields().get("reason")).contains("deadline");
    }

    @Test
    void elementsExposed() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}", tokenizer);
        assertThat(matcher.elements()).isNotEmpty();
        assertThat(matcher.elements().stream()
            .filter(e -> e.kind() == StructuralMatcher.ElementKind.CLAUSE)
            .count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void stringVariableReturnsSurface() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}", tokenizer);
        var tokens = tokenizer.tokenize("I approve this design");
        var result = matcher.match(tokens, Map.of());
        assertThat(result).isPresent();
        assertThat(result.get().fields().get("subject")).isEqualTo("design");
    }
}
