package io.casehub.platform.compiler;

import io.casehub.platform.coordination.protocol.ProtocolDefinition;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateSpecTest {

    @Test
    void createsWithProtocolDefinitionAndConstraints() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("vote", "I ${decision} this ${subject}");
        var protocol = new ProtocolDefinition("test", templates);
        var constraints = Map.of("vote",
                Map.<String, VariableConstraint>of("decision", VariableConstraint.enumOf("approve", "reject")));
        var spec = new TemplateSpec(protocol, constraints);
        assertThat(spec.protocolDefinition().name()).isEqualTo("test");
        assertThat(spec.variableConstraints()).containsKey("vote");
    }

    @Test
    void emptyConstraintsAllowed() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("ack", "I confirm ${item}");
        var protocol = new ProtocolDefinition("test", templates);
        var spec = new TemplateSpec(protocol, Map.of());
        assertThat(spec.variableConstraints()).isEmpty();
    }

    @Test
    void rejectsNullProtocolDefinition() {
        assertThatThrownBy(() -> new TemplateSpec(null, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullConstraints() {
        var templates = new LinkedHashMap<String, String>();
        templates.put("ack", "I confirm ${item}");
        var protocol = new ProtocolDefinition("test", templates);
        assertThatThrownBy(() -> new TemplateSpec(protocol, null))
                .isInstanceOf(NullPointerException.class);
    }
}
