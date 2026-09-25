package io.casehub.yaml.core.step;

import java.util.Map;

public record StepDefinitionFile(
        String namespace,
        Map<String, StepDefinition> actions) {

    public StepDefinitionFile {
        if (namespace == null) namespace = "";
        actions = Map.copyOf(actions);
    }
}
