package io.casehub.yaml.core.step;

import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.Portability;

import java.util.Map;

public record Declaration(
        String name,
        String description,
        Map<String, Parameter> inputs,
        Map<String, Parameter> outputs,
        InvokeBinding invoke,
        Portability portability) {

    public Declaration(String name, String description, Map<String, Parameter> inputs,
                       Map<String, Parameter> outputs, InvokeBinding invoke) {
        this(name, description, inputs, outputs, invoke, null);
    }

    public Declaration {
        if (inputs == null) inputs = Map.of();
        if (outputs == null) outputs = Map.of();
    }

    public String qualifiedName(String namespace) {
        return namespace.isEmpty() ? name : namespace + "." + name;
    }
}
