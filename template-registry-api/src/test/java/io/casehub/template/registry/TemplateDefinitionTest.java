package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateDefinitionTest {

    @Test
    void builderCreatesDefinition() {
        var def = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}")
            .variable("decision", VariableConstraint.enumOf("approve", "reject"))
            .variable("subject", VariableConstraint.string())
            .build();

        assertThat(def.name()).isEqualTo("vote");
        assertThat(def.patterns()).containsExactly("I ${decision} this ${subject}");
        assertThat(def.variables()).containsKeys("decision", "subject");
    }

    @Test
    void multiplePatterns() {
        var def = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}")
            .pattern("${decision}")
            .build();

        assertThat(def.patterns()).hasSize(2);
    }

    @Test
    void nullNameThrows() {
        assertThatThrownBy(() -> new TemplateDefinition(null, List.of("pat"), Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void emptyPatternsThrows() {
        assertThatThrownBy(() -> new TemplateDefinition("x", List.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void patternsAreDefensivelyCopied() {
        var patterns = new ArrayList<>(List.of("pat"));
        var def = new TemplateDefinition("x", patterns, Map.of());
        patterns.add("extra");
        assertThat(def.patterns()).hasSize(1);
    }

    @Test
    void nullVariablesDefaultsToEmptyMap() {
        var def = new TemplateDefinition("x", List.of("pat"), null);
        assertThat(def.variables()).isEmpty();
    }

    @Test
    void messageFormatFieldNullByDefault() {
        var def = TemplateDefinition.builder("test")
                                    .pattern("hello ${name}")
                                    .build();
        assertThat(def.messageFormat()).isNull();
    }

    @Test
    void messageFormatFieldSetViaBuilder() {
        var def = TemplateDefinition.builder("test")
                                    .pattern("${count} items")
                                    .messageFormat(".match {$count :integer}\n1 {{{$count} item}}\n* {{{$count} items}}")
                                    .build();
        assertThat(def.messageFormat()).startsWith(".match");
    }

    @Test
    void threeArgConstructorSetsMessageFormatNull() {
        var def = new TemplateDefinition("test", List.of("hello ${name}"), Map.of());
        assertThat(def.messageFormat()).isNull();
    }

    @Test
    void fourArgConstructorSetsMessageFormat() {
        var mf  = ".match {$count :integer}\n1 {{one}}\n* {{many}}";
        var def = new TemplateDefinition("test", List.of("${count} items"), Map.of(), mf);
        assertThat(def.messageFormat()).isEqualTo(mf);
    }

}
