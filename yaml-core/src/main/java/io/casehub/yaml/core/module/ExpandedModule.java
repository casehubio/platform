package io.casehub.yaml.core.module;

import java.util.Map;

public record ExpandedModule(
        Map<String, Map<String, Object>> sections,
        Map<String, Map<String, String>> moduleScopes,
        Map<String, String> importConditions) {}
