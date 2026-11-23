package io.casehub.platform.compiler;

import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybookCompilerTemplateTest {

    private final PlaybookCompiler compiler = new PlaybookCompiler();

    @Test
    void compilesTemplatesSection() {
        var content = new LinkedHashMap<String, Object>();
        content.put("templates", Map.of(
                "vote", Map.of("pattern", "I ${decision} this ${subject}")
        ));
        content.put("steps", List.of());
        var outcome = compiler.compile("test-playbook", content);
        assertThat(outcome).isInstanceOf(CompilationOutcome.Success.class);
        var result = ((CompilationOutcome.Success) outcome).result();
        assertThat(result.templateSpec()).isNotNull();
        assertThat(result.templateSpec().protocolDefinition().name()).isEqualTo("test-playbook");
        assertThat(result.templateSpec().protocolDefinition().templates()).containsKey("vote");
    }

    @Test
    void parsesEnumVariableConstraints() {
        var content = new LinkedHashMap<String, Object>();
        content.put("templates", Map.of(
                "vote", Map.of(
                        "pattern", "I ${decision} this design",
                        "variables", Map.of("decision",
                                Map.of("type", "enum", "values", List.of("approve", "reject")))
                )
        ));
        content.put("steps", List.of());
        var result = ((CompilationOutcome.Success) compiler.compile("test", content)).result();
        var constraints = result.templateSpec().variableConstraints().get("vote");
        assertThat(constraints).containsKey("decision");
        assertThat(constraints.get("decision")).isInstanceOf(VariableConstraint.EnumConstraint.class);
        assertThat(constraints.get("decision").required()).isTrue();
    }

    @Test
    void parsesOptionalStringConstraint() {
        var content = new LinkedHashMap<String, Object>();
        content.put("templates", Map.of(
                "greet", Map.of(
                        "pattern", "Hello ${name}",
                        "variables", Map.of("name",
                                Map.of("type", "string", "required", false))
                )
        ));
        content.put("steps", List.of());
        var result = ((CompilationOutcome.Success) compiler.compile("test", content)).result();
        var constraints = result.templateSpec().variableConstraints().get("greet");
        assertThat(constraints.get("name").required()).isFalse();
    }

    @Test
    void noTemplatesSectionReturnsNullTemplateSpec() {
        var content = new LinkedHashMap<String, Object>();
        content.put("steps", List.of());
        var result = ((CompilationOutcome.Success) compiler.compile("test", content)).result();
        assertThat(result.templateSpec()).isNull();
    }

    @Test
    void multipleTemplatesParsed() {
        var content = new LinkedHashMap<String, Object>();
        var templates = new LinkedHashMap<String, Object>();
        templates.put("vote", Map.of("pattern", "I ${decision} this design"));
        templates.put("escalate", Map.of("pattern", "Escalating to ${target}"));
        content.put("templates", templates);
        content.put("steps", List.of());
        var result = ((CompilationOutcome.Success) compiler.compile("test", content)).result();
        assertThat(result.templateSpec().protocolDefinition().templates()).hasSize(2);
    }

    @Test
    void templateWithoutVariablesHasEmptyConstraints() {
        var content = new LinkedHashMap<String, Object>();
        content.put("templates", Map.of(
                "ack", Map.of("pattern", "I confirm ${item}")
        ));
        content.put("steps", List.of());
        var result = ((CompilationOutcome.Success) compiler.compile("test", content)).result();
        assertThat(result.templateSpec().variableConstraints()).doesNotContainKey("ack");
    }

    @Test
    void defaultStringConstraintIsRequired() {
        var content = new LinkedHashMap<String, Object>();
        content.put("templates", Map.of(
                "greet", Map.of(
                        "pattern", "Hello ${name}",
                        "variables", Map.of("name", Map.of("type", "string"))
                )
        ));
        content.put("steps", List.of());
        var result = ((CompilationOutcome.Success) compiler.compile("test", content)).result();
        var constraints = result.templateSpec().variableConstraints().get("greet");
        assertThat(constraints.get("name").required()).isTrue();
    }
}
