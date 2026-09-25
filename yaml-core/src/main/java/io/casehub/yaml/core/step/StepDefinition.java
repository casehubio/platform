package io.casehub.yaml.core.step;

import java.util.Map;

public record StepDefinition(
        String name,
        String description,
        Map<String, StepParameter> inputs,
        Map<String, StepParameter> outputs,
        InvokeBinding invoke) {

    public StepDefinition {
        if (inputs == null) inputs = Map.of();
        if (outputs == null) outputs = Map.of();
    }

    public String qualifiedName(String namespace) {
        return namespace.isEmpty() ? name : namespace + "." + name;
    }
}
