package io.casehub.yaml.step.runner;

import io.casehub.yaml.core.error.ParseError;
import io.casehub.yaml.core.error.SourceLocation;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.CompositePluginRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationSuggestionGeneratorTest {

    private final CompositePluginRegistry registry = new CompositePluginRegistry() {{
        register(Definition.of("log")
                .input("message", new Parameter(ParameterType.STRING, true, null, null, null, null))
                .execute((p, s) -> Result.of(Map.of()))
                .build());
        register(Definition.of("assert")
                .input("expected", new Parameter(ParameterType.STRING, true, null, null, null, null))
                .execute((p, s) -> Result.of(Map.of()))
                .build());
        register(Definition.of("process")
                .input("command", new Parameter(ParameterType.STRING, true, null, null, null, null))
                .execute((p, s) -> Result.of(Map.of()))
                .build());
    }};

    @Test
    void suggestsClosestMatchForUnknownStep() {
        var error = new ParseError.UnknownStepError(null, "lg", SourceLocation.UNKNOWN, null);
        var suggestions = ValidationSuggestionGenerator.generate(List.of(error), registry);
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).suggestion()).contains("log");
    }

    @Test
    void includesExampleYaml() {
        var error = new ParseError.UnknownStepError(null, "lg", SourceLocation.UNKNOWN, null);
        var suggestions = ValidationSuggestionGenerator.generate(List.of(error), registry);
        assertThat(suggestions.get(0).exampleYaml()).contains("log");
        assertThat(suggestions.get(0).exampleYaml()).contains("message");
    }

    @Test
    void noSuggestionForCompletelyDifferentName() {
        var error = new ParseError.UnknownStepError(null, "xyzzy-totally-different", SourceLocation.UNKNOWN, null);
        var suggestions = ValidationSuggestionGenerator.generate(List.of(error), registry);
        assertThat(suggestions).isEmpty();
    }

    @Test
    void diagnosticIndexCorrelates() {
        var error0 = new ParseError.StructureError(null, "some structure error", SourceLocation.UNKNOWN, null);
        var error1 = new ParseError.UnknownStepError(null, "procss", SourceLocation.UNKNOWN, null);
        var suggestions = ValidationSuggestionGenerator.generate(List.of(error0, error1), registry);
        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).diagnosticIndex()).isEqualTo(1);
        assertThat(suggestions.get(0).suggestion()).contains("process");
    }

    @Test
    void levenshteinBasicCases() {
        assertThat(ValidationSuggestionGenerator.levenshtein("", "")).isEqualTo(0);
        assertThat(ValidationSuggestionGenerator.levenshtein("abc", "abc")).isEqualTo(0);
        assertThat(ValidationSuggestionGenerator.levenshtein("abc", "abd")).isEqualTo(1);
        assertThat(ValidationSuggestionGenerator.levenshtein("kitten", "sitting")).isEqualTo(3);
    }

    @Test
    void buildExampleForActionWithInputs() {
        String example = ValidationSuggestionGenerator.buildExample("log", registry);
        assertThat(example).contains("- log:");
        assertThat(example).contains("message:");
    }

    @Test
    void buildExampleForActionWithNoInputs() {
        var reg = new CompositePluginRegistry();
        reg.register(Definition.of("noop")
                .execute((p, s) -> Result.of(Map.of()))
                .build());
        String example = ValidationSuggestionGenerator.buildExample("noop", reg);
        assertThat(example).isEqualTo("- noop: {}");
    }
}
