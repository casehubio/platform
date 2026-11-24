package io.casehub.template.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateGeneratorTest {

    @Test
    void simpleSubstitution() {
        var def = new TemplateDefinition("vote",
            List.of("I ${decision} this ${subject}"), Map.of());
        var result = TemplateGenerator.generate(def, Map.of(
            "decision", "approve", "subject", "design"));
        assertThat(result).isEqualTo("I approve this design");
    }

    @Test
    void optionalSegmentIncludedWhenAllPresent() {
        var def = new TemplateDefinition("vote",
            List.of("I ${decision} this[ because ${rationale}]"), Map.of());
        var result = TemplateGenerator.generate(def, Map.of(
            "decision", "approve", "rationale", "it is clean"));
        assertThat(result).isEqualTo("I approve this because it is clean");
    }

    @Test
    void optionalSegmentOmittedWhenMissing() {
        var def = new TemplateDefinition("vote",
            List.of("I ${decision} this[ because ${rationale}]"), Map.of());
        var result = TemplateGenerator.generate(def, Map.of(
            "decision", "approve"));
        assertThat(result).isEqualTo("I approve this");
    }

    @Test
    void missingRequiredVariableThrows() {
        var def = new TemplateDefinition("vote",
            List.of("I ${decision} this ${subject}"), Map.of());
        assertThatThrownBy(() -> TemplateGenerator.generate(def, Map.of(
            "decision", "approve")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("subject");
    }

    @Test
    void usesPatternZero() {
        var def = new TemplateDefinition("vote",
            List.of("I ${decision} this ${subject}", "${decision}"),
            Map.of());
        var result = TemplateGenerator.generate(def, Map.of(
            "decision", "approve", "subject", "design"));
        assertThat(result).isEqualTo("I approve this design");
    }

    @Test
    void specialCharsInValues() {
        var def = new TemplateDefinition("msg",
            List.of("Value is ${val}"), Map.of());
        var result = TemplateGenerator.generate(def, Map.of(
            "val", "$100 (USD)"));
        assertThat(result).isEqualTo("Value is $100 (USD)");
    }
}
