package io.casehub.template.registry.nlp.svo;

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
            .filter(e -> e.kind() == io.casehub.template.registry.ElementKind.CLAUSE)
            .count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void optionalSegmentMatchesWithoutOptional() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}[ because ${rationale}]", tokenizer);
        var constraints = Map.of("decision",
            VariableConstraint.enumOf("approve", "reject", "abstain"));
        var tokens = tokenizer.tokenize("I approved this design");
        var result = matcher.match(tokens, constraints);
        assertThat(result).isPresent();
        assertThat(result.get().fields()).containsEntry("decision", "approve");
        assertThat(result.get().fields()).containsEntry("subject", "design");
    }

    @Test
    void optionalSegmentMatchesWithOptional() {
        var matcher = StructuralMatcher.compile(
            "I ${decision} this ${subject}[ because ${rationale}]", tokenizer);
        var tokens = tokenizer.tokenize("I approve this design because it is clean");
        var result = matcher.match(tokens, Map.of());
        assertThat(result).isPresent();
        assertThat(result.get().fields()).containsKey("rationale");
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

    @Test
    void adjacentClauseVariablesThrowAtCompileTime() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                StructuralMatcher.compile("${agent} ${action} ${target}", tokenizer)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agent")
                .hasMessageContaining("action");
    }

    @Test
    void adjacentClauseVariablesInMiddleThrow() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                StructuralMatcher.compile("Send ${message} ${recipient} now", tokenizer)
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message")
                .hasMessageContaining("recipient");
    }

    @Test
    void separatedClauseVariablesCompileSuccessfully() {
        var matcher = StructuralMatcher.compile(
                "On behalf of ${agent} I ${action} this ${target}", tokenizer);
        assertThat(matcher.elements()).isNotEmpty();
    }

    @Test
    void multiWordEnumValueMatchesFullClause() {
        var matcher = StructuralMatcher.compile(
                "I ${decision} this ${subject}", tokenizer);
        var constraints = Map.of("decision",
                VariableConstraint.enumOf("strongly approve", "conditionally accept", "reject"));
        var tokens = tokenizer.tokenize("I strongly approve this design");
        var result = matcher.match(tokens, constraints);
        assertThat(result).isPresent();
        assertThat(result.get().fields().get("decision"))
                .as("multi-word enum value should match full clause, not just first token")
                .isEqualTo("strongly approve");
    }

    @Test
    void multiWordEnumWithInflection() {
        var matcher = StructuralMatcher.compile(
                "I ${decision} this ${subject}", tokenizer);
        var constraints = Map.of("decision",
                VariableConstraint.enumOf("strongly approve", "conditionally accept", "reject"));
        var tokens = tokenizer.tokenize("I strongly approved this design");
        var result = matcher.match(tokens, constraints);
        assertThat(result).isPresent();
        assertThat(result.get().fields().get("decision"))
                .as("inflected multi-word enum should lemmatise to base form")
                .isEqualTo("strongly approve");
    }

    @Test
    void singleWordEnumStillWorks() {
        var matcher = StructuralMatcher.compile(
                "I ${decision} this ${subject}", tokenizer);
        var constraints = Map.of("decision",
                VariableConstraint.enumOf("approve", "reject"));
        var tokens = tokenizer.tokenize("I approved this design");
        var result = matcher.match(tokens, constraints);
        assertThat(result).isPresent();
        assertThat(result.get().fields().get("decision"))
                .isEqualTo("approve");
    }

    @Test
    void matchWithTraceReportsElementsOnSuccess() {
        var matcher = StructuralMatcher.compile(
                "I ${decision} this ${subject}", tokenizer);
        var tokens = tokenizer.tokenize("I approve this design");
        var result = matcher.matchWithTrace(tokens, Map.of());
        assertThat(result.result()).isPresent();
        assertThat(result.elements()).isNotEmpty();
        assertThat(result.elements().get(0).kind())
                .isEqualTo(io.casehub.template.registry.ElementKind.LITERAL);
        assertThat(result.elements().get(0).matched()).isTrue();
    }

    @Test
    void matchWithTraceReportsFailedLiteral() {
        var matcher = StructuralMatcher.compile(
                "I ${decision} this ${subject}", tokenizer);
        var tokens = tokenizer.tokenize("the design looks good to me");
        var result = matcher.matchWithTrace(tokens, Map.of());
        assertThat(result.result()).isEmpty();
        assertThat(result.elements()).isNotEmpty();
        var firstLiteral = result.elements().stream()
                .filter(e -> e.kind() == io.casehub.template.registry.ElementKind.LITERAL)
                .findFirst().orElseThrow();
        assertThat(firstLiteral.matched()).isFalse();
        assertThat(firstLiteral.expected()).isEqualTo("i");
    }

    @Test
    void matchWithTraceReportsVariableCapture() {
        var matcher = StructuralMatcher.compile(
                "I ${decision} this ${subject}", tokenizer);
        var tokens = tokenizer.tokenize("I approve this design");
        var result = matcher.matchWithTrace(tokens, Map.of());
        var clauseElements = result.elements().stream()
                .filter(e -> e.kind() == io.casehub.template.registry.ElementKind.CLAUSE)
                .toList();
        assertThat(clauseElements).isNotEmpty();
        assertThat(clauseElements.stream().anyMatch(e ->
                "decision".equals(e.expected()) && e.matched())).isTrue();
    }
}
