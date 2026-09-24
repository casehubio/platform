package io.casehub.yaml.core.module;

import java.util.Map;

public record YamlImport(
        String module,
        String as,
        String when,
        Map<String, String> parameters,
        Object forEach,
        Object loop) {

    public YamlImport {
        if (parameters == null) { parameters = Map.of(); }
    }

    public YamlImport(String module, String as, String when, Map<String, String> parameters) {
        this(module, as, when, parameters, null, null);
    }
}
