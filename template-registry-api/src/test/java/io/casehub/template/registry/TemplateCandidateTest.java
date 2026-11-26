package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateCandidateTest {

    @Test
    void fromDefinition_extractsFields() {
        var def = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}")
            .variable("decision", VariableConstraint.enumOf("approve", "reject"))
            .variable("subject", VariableConstraint.string())
            .build();
        var candidate = TemplateCandidate.from(def);
        assertThat(candidate.templateName()).isEqualTo("vote");
        assertThat(candidate.patterns()).containsExactly("I ${decision} this ${subject}");
        assertThat(candidate.variables()).containsKey("decision");
        assertThat(candidate.variables()).containsKey("subject");
    }

    @Test
    void nullVariables_defaultsToEmptyMap() {
        var candidate = new TemplateCandidate("t1",
            java.util.List.of("pattern"), null);
        assertThat(candidate.variables()).isEmpty();
    }

    @Test
    void requiresNonNullTemplateName() {
        assertThatThrownBy(() -> new TemplateCandidate(
            null, java.util.List.of("p"), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void patternsAreImmutable() {
        var candidate = TemplateCandidate.from(
            TemplateDefinition.builder("t").pattern("p").build());
        assertThatThrownBy(() -> candidate.patterns().add("new"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
