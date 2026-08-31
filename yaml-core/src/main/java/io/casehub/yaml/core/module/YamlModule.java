package io.casehub.yaml.core.module;

import java.util.Map;

public record YamlModule(
        String name,
        Map<String, YamlModuleParameter> parameters,
        Map<String, Map<String, Object>> sections) {

    public YamlModule {
        if (parameters == null) { parameters = Map.of(); }
        if (sections == null) { sections = Map.of(); }
    }
}
