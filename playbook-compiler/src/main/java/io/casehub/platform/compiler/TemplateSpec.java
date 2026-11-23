package io.casehub.platform.compiler;

import io.casehub.platform.coordination.protocol.ProtocolDefinition;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.Map;
import java.util.Objects;

public record TemplateSpec(
        ProtocolDefinition protocolDefinition,
        Map<String, Map<String, VariableConstraint>> variableConstraints
) {
    public TemplateSpec {
        Objects.requireNonNull(protocolDefinition, "protocolDefinition");
        Objects.requireNonNull(variableConstraints, "variableConstraints");
        variableConstraints = Map.copyOf(variableConstraints);
    }
}
