package io.casehub.yaml.core.step;

import java.util.Map;

public record DeclarationFile(
        String namespace,
        Map<String, Declaration> actions) {

    public DeclarationFile {
        if (namespace == null) namespace = "";
        actions = Map.copyOf(actions);
    }
}
