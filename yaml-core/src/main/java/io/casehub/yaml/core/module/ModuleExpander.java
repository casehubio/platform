package io.casehub.yaml.core.module;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleExpander {

    private ModuleExpander() {}

    public static ExpandedModule expand(
            List<YamlImport> imports,
            Map<String, YamlModule> availableModules,
            Map<String, Map<String, Object>> existingSections) {
        return expand(imports, availableModules, existingSections, null, null);
    }

    public static ExpandedModule expand(
            List<YamlImport> imports,
            Map<String, YamlModule> availableModules,
            Map<String, Map<String, Object>> existingSections,
            SectionDeserializer deserializer,
            SectionContentRewriter rewriter) {

        validateImports(imports, availableModules);

        Map<String, Map<String, Object>> mergedSections = new LinkedHashMap<>();
        Map<String, Map<String, String>> moduleScopes = new LinkedHashMap<>();
        Map<String, String> importConditions = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : existingSections.entrySet()) {
            mergedSections.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }

        for (YamlImport imp : imports) {
            YamlModule module = availableModules.get(imp.module());
            Map<String, String> paramScope = resolveParameters(module, imp);
            moduleScopes.put(imp.as(), paramScope);
            importConditions.put(imp.as(), imp.when());

            for (Map.Entry<String, Map<String, Object>> sectionEntry : module.sections().entrySet()) {
                String sectionName = sectionEntry.getKey();
                Map<String, Object> sectionContent = sectionEntry.getValue();

                Map<String, Object> targetSection = mergedSections
                        .computeIfAbsent(sectionName, k -> new LinkedHashMap<>());

                for (Map.Entry<String, Object> contentEntry : sectionContent.entrySet()) {
                    String prefixedKey = imp.as() + "." + contentEntry.getKey();
                    Object value = contentEntry.getValue();
                    if (deserializer != null && value instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rawMap = (Map<String, Object>) value;
                        value = deserializer.deserialize(sectionName, contentEntry.getKey(), rawMap);
                    }
                    if (rewriter != null) {
                        value = rewriter.rewrite(sectionName, contentEntry.getKey(), value,
                                imp.as(), sectionContent.keySet());
                    }
                    targetSection.put(prefixedKey, value);
                }
            }
        }

        return new ExpandedModule(
                Map.copyOf(mergedSections),
                Map.copyOf(moduleScopes),
                java.util.Collections.unmodifiableMap(importConditions));
    }

    private static void validateImports(List<YamlImport> imports,
                                         Map<String, YamlModule> availableModules) {
        List<String> errors = new ArrayList<>();
        Set<String> seenAliases = new HashSet<>();

        for (YamlImport imp : imports) {
            if (!availableModules.containsKey(imp.module())) {
                errors.add("Import references unknown module '" + imp.module() + "'.");
            }

            if (imp.as() == null || imp.as().isBlank()) {
                errors.add("Import of module '" + imp.module()
                        + "' is missing a required alias (as).");
            } else {
                if (imp.as().contains(".")) {
                    errors.add("Import alias '" + imp.as()
                            + "' contains '.', which is reserved as the ID separator.");
                }
                if (!seenAliases.add(imp.as())) {
                    errors.add("Import alias '" + imp.as()
                            + "' is a duplicate — each import must have a unique alias.");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(
                    "Import validation failed: " + String.join(" ", errors));
        }
    }

    private static Map<String, String> resolveParameters(YamlModule module,
                                                          YamlImport imp) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, YamlModuleParameter> entry : module.parameters().entrySet()) {
            String name = entry.getKey();
            YamlModuleParameter param = entry.getValue();
            String value = imp.parameters().get(name);
            if (value == null && param.defaultValue() != null) {
                value = param.defaultValue();
            }
            if (value != null) {
                resolved.put(name, value);
            }
        }

        ParameterValidator.validateOrThrow(module.parameters(), imp.parameters());

        return Map.copyOf(resolved);
    }
}
