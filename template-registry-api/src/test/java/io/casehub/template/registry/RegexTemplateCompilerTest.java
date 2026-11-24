package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegexTemplateCompilerTest {

    @Test
    void simpleExtraction() {
        var compiled = RegexTemplateCompiler.compile("I ${decision} this ${subject}");
        var result = compiled.extract("I approve this design");
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("decision", "approve")
            .containsEntry("subject", "design");
    }

    @Test
    void optionalSegmentPresent() {
        var compiled = RegexTemplateCompiler.compile(
            "I ${decision} this ${subject}[ because ${rationale}]");
        var result = compiled.extract("I approve this design because it is clean");
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("rationale", "it is clean");
    }

    @Test
    void optionalSegmentAbsent() {
        var compiled = RegexTemplateCompiler.compile(
            "I ${decision} this ${subject}[ because ${rationale}]");
        var result = compiled.extract("I approve this design");
        assertThat(result).isPresent();
        assertThat(result.get()).containsKey("decision")
            .doesNotContainKey("rationale");
    }

    @Test
    void noMatchReturnsEmpty() {
        var compiled = RegexTemplateCompiler.compile("I ${decision} this ${subject}");
        var result = compiled.extract("Something completely different");
        assertThat(result).isEmpty();
    }

    @Test
    void enumConstraintPasses() {
        var compiled = RegexTemplateCompiler.compile("I ${decision} this");
        var constraints = Map.of("decision",
            VariableConstraint.enumOf("approve", "reject"));
        var result = compiled.extract("I approve this", constraints);
        assertThat(result).isPresent();
    }

    @Test
    void enumConstraintFails() {
        var compiled = RegexTemplateCompiler.compile("I ${decision} this");
        var constraints = Map.of("decision",
            VariableConstraint.enumOf("approve", "reject"));
        var result = compiled.extract("I maybe this", constraints);
        assertThat(result).isEmpty();
    }

    @Test
    void variableNames() {
        var compiled = RegexTemplateCompiler.compile(
            "I ${decision} this ${subject}");
        assertThat(compiled.variableNames())
            .containsExactly("decision", "subject");
    }

    @Test
    void dottedVariableName() {
        var compiled = RegexTemplateCompiler.compile("Value is ${config.key}");
        var result = compiled.extract("Value is hello");
        assertThat(result).isPresent();
        assertThat(result.get()).containsEntry("config.key", "hello");
    }

    @Test
    void duplicateVariableNameThrows() {
        assertThatThrownBy(() ->
            RegexTemplateCompiler.compile("${x} and ${x}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate");
    }
}
