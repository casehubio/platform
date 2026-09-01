package io.casehub.yaml.core.module;

import java.util.Map;

public record ExpandedModule(
        Map<String, Map<String, Object>> sections,
        Map<String, Map<String, String>> moduleScopes,
        Map<String, String> importConditions) {

    @SuppressWarnings("unchecked")
    public <T> Map<String, T> section(String name) {
        return (Map<String, T>) (Map<String, ?>)
                                        sections.getOrDefault(name, Map.of());
    }
}
