package io.casehub.yaml.core.module;

import io.casehub.yaml.core.resolver.VariableSource;

import java.util.Map;

public record TypedExpandedModule<T>(
        T content,
        Map<String, Map<String, String>> moduleScopes,
        Map<String, String> importConditions,
        Map<String, Map<String, String>> moduleOutputs) {

    public VariableSource outputSource() {
        return name -> {
            int dot = name.indexOf('.');
            if (dot < 0) return null;
            String alias = name.substring(0, dot);
            String outputName = name.substring(dot + 1);
            Map<String, String> outputs = moduleOutputs.get(alias);
            return outputs != null ? outputs.get(outputName) : null;
        };
    }
}
