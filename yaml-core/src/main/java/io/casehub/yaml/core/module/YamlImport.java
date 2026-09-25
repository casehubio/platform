package io.casehub.yaml.core.module;

import java.util.Map;

public record YamlImport(
        String module,
        String steps,
        String as,
        String when,
        Map<String, String> parameters,
        Object forEach,
        Object loop) {

    public YamlImport {
        if (module != null && steps != null) {
            throw new IllegalArgumentException(
                    "Import 'module' and 'steps' are mutually exclusive — specify one, not both.");
        }
        if (module == null && steps == null) {
            throw new IllegalArgumentException(
                    "Import must specify either 'module' or 'steps'.");
        }
        if (parameters == null) {parameters = Map.of();}
    }

    public YamlImport(String module, String as, String when, Map<String, String> parameters,
                      Object forEach, Object loop) {
        this(module, null, as, when, parameters, forEach, loop);
    }

    public YamlImport(String module, String as, String when, Map<String, String> parameters) {
        this(module, null, as, when, parameters, null, null);
    }
}
