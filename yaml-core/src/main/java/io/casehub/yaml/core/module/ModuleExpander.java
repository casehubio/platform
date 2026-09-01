package io.casehub.yaml.core.module;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.VariableSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ModuleExpander {

    private static final Pattern VAR_REF = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern WHOLE_MODULE_REF =
            Pattern.compile("^\\s*\\$\\{module\\.[^}]+}\\s*$");


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
        validateModuleRefs(imports, availableModules);

        Map<String, Map<String, Object>> mergedSections = new LinkedHashMap<>();
        Map<String, Map<String, String>> moduleScopes = new LinkedHashMap<>();
        Map<String, String> importConditions = new LinkedHashMap<>();
        Map<String, Map<String, String>> allOutputs = new LinkedHashMap<>();

        for (Map.Entry<String, Map<String, Object>> entry : existingSections.entrySet()) {
            mergedSections.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }

        for (YamlImport imp : imports) {
            YamlModule module = availableModules.get(imp.module());
            Map<String, String> resolvedParams = resolveModuleRefsInParams(
                    imp.parameters(), allOutputs, imp.as());
            Map<String, String> paramScope = resolveParameters(module, imp, resolvedParams);
            moduleScopes.put(imp.as(), paramScope);
            importConditions.put(imp.as(), imp.when());

            Map<String, String> resolvedOutputs = resolveOutputs(module, paramScope);
            allOutputs.put(imp.as(), resolvedOutputs);

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
                java.util.Collections.unmodifiableMap(importConditions),
                Map.copyOf(allOutputs));
    }

    private static void validateImports(List<YamlImport> imports,
                                         Map<String, YamlModule> availableModules) {
        List<String> errors = new ArrayList<>();
        Set<String> seenAliases = new HashSet<>();

        for (YamlImport imp : imports) {
            if (!availableModules.containsKey(imp.module())) {
                errors.add("Import references unknown module '" + imp.module() + "'.");
            } else {
                validateOutputNames(availableModules.get(imp.module()), errors);
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
                                                          YamlImport imp,
                                                          Map<String, String> resolvedParams) {
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, YamlModuleParameter> entry : module.parameters().entrySet()) {
            String name = entry.getKey();
            YamlModuleParameter param = entry.getValue();
            String value = resolvedParams.get(name);
            if (value == null && param.defaultValue() != null) {
                value = param.defaultValue();
            }
            if (value != null) {
                resolved.put(name, value);
            }
        }

        ParameterValidator.validateOrThrow(module.parameters(), resolvedParams);

        return Map.copyOf(resolved);
    }

    private static Map<String, String> resolveModuleRefsInParams(
            Map<String, String> rawParams,
            Map<String, Map<String, String>> allOutputs,
            String currentAlias) {
        if (rawParams.values().stream().noneMatch(v -> v.contains("${module."))) {
            return rawParams;
        }
        VariableSource moduleSource = buildModuleSource(allOutputs);
        VariableResolver resolver = new VariableResolver(
                Map.of("module", moduleSource), Set.of());
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            String value = entry.getValue();
            if (value.contains("${module.")) {
                Matcher guardMatcher = VAR_REF.matcher(value);
                while (guardMatcher.find()) {
                    String guardKey = guardMatcher.group(1);
                    if (!guardKey.startsWith("module.")) continue;
                    String rest = guardKey.substring("module.".length());
                    int dot = rest.indexOf('.');
                    if (dot < 0) continue;
                    String refAlias = rest.substring(0, dot);
                    if (!allOutputs.containsKey(refAlias)) {
                        throw new IllegalStateException(
                                "Forward reference to '" + refAlias + "' in import '"
                                + currentAlias
                                + "' — should have been caught by validateModuleRefs.");
                    }
                }
                value = resolver.resolveString(value, currentAlias + "." + entry.getKey());
            }
            resolved.put(entry.getKey(), value);
        }
        return resolved;
    }

    private static VariableSource buildModuleSource(
            Map<String, Map<String, String>> allOutputs) {
        return name -> {
            int dot = name.indexOf('.');
            if (dot < 0) return null;
            String alias = name.substring(0, dot);
            String outputName = name.substring(dot + 1);
            Map<String, String> outputs = allOutputs.get(alias);
            return outputs != null ? outputs.get(outputName) : null;
        };
    }

    private static void validateOutputNames(YamlModule module, List<String> errors) {
        for (String outputName : module.outputs().keySet()) {
            if (outputName == null || outputName.isBlank()) {
                errors.add("Module '" + module.name() + "' has a blank output name.");
            } else if (outputName.contains(".")) {
                errors.add("Output '" + outputName + "' in module '" + module.name()
                        + "' contains '.', which is reserved as the alias/name separator.");
            } else if (outputName.contains("${")) {
                errors.add("Output '" + outputName + "' in module '" + module.name()
                        + "' contains '${', which is reserved for variable references.");
            }
        }
    }

    private static boolean isWholeModuleRef(String value) {
        return WHOLE_MODULE_REF.matcher(value).matches();
    }

    private static YamlModule findModuleByAlias(String alias,
                                                List<YamlImport> imports,
                                                Map<String, YamlModule> availableModules) {
        for (YamlImport imp : imports) {
            if (alias.equals(imp.as())) {
                return availableModules.get(imp.module());
            }
        }
        return null;
    }

    private static void validateModuleRefs(List<YamlImport> imports,
                                           Map<String, YamlModule> availableModules) {
        List<ParameterViolation> violations       = new ArrayList<>();
        Set<String>              processedAliases = new HashSet<>();

        for (YamlImport imp : imports) {
            YamlModule targetModule = availableModules.get(imp.module());
            if (targetModule == null) {
                processedAliases.add(imp.as());
                continue;
            }

            for (Map.Entry<String, String> paramEntry : imp.parameters().entrySet()) {
                String paramName  = paramEntry.getKey();
                String paramValue = paramEntry.getValue();

                if (!paramValue.contains("${module.")) {continue;}

                YamlModuleParameter paramDecl    = targetModule.parameters().get(paramName);
                boolean             isWholeValue = isWholeModuleRef(paramValue);

                Matcher matcher = VAR_REF.matcher(paramValue);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (!key.startsWith("module.")) {continue;}
                    String rest = key.substring("module.".length());
                    int    dot  = rest.indexOf('.');
                    if (dot < 0) {continue;}
                    String refAlias   = rest.substring(0, dot);
                    String outputName = rest.substring(dot + 1);

                    if (!processedAliases.contains(refAlias)) {
                        violations.add(new ParameterViolation(paramName,
                                                              "module-ref-forward",

                                                              "Import '" + imp.as() + "' references ${" + key
                                                              + "}, but '" + refAlias
                                                              + "' has not been imported yet. Move the '"
                                                              + refAlias + "' import before '" + imp.as() + "'.",
                                                              paramValue, null));
                        continue;
                    }

                    YamlModule refModule = findModuleByAlias(refAlias, imports,
                                                             availableModules);
                    if (refModule == null) {continue;}

                    YamlModuleOutput output = refModule.outputs().get(outputName);
                    if (output == null) {
                        violations.add(new ParameterViolation(paramName,
                                                              "module-ref-missing-output",
                                                              "Import '" + imp.as() + "' references output '"
                                                              + outputName + "' on '" + refAlias
                                                              + "', but module '" + refModule.name()
                                                              + "' does not declare that output. Available: "
                                                              + refModule.outputs().keySet(),
                                                              paramValue, null));
                        continue;
                    }

                    if (isWholeValue && paramDecl != null) {
                        if (!paramDecl.type().canAccept(output.type())) {
                            violations.add(new ParameterViolation(paramName,
                                                                  "module-ref-type-incompatible",
                                                                  "Output '" + outputName + "' (" + output.type()
                                                                  + ") on '" + refAlias
                                                                  + "' is not assignable to parameter '"
                                                                  + paramName + "' (" + paramDecl.type() + ").",
                                                                  paramValue, null));
                        }
                    }
                }

                if (!isWholeValue && paramDecl != null
                    && paramDecl.type() != ParameterType.STRING) {
                    violations.add(new ParameterViolation(paramName,
                                                          "module-ref-embedded-type",
                                                          "Parameter '" + paramName + "' (" + paramDecl.type()
                                                          + ") uses string interpolation, which always "
                                                          + "produces STRING.",
                                                          paramValue, null));
                }
            }
            processedAliases.add(imp.as());
        }

        if (!violations.isEmpty()) {
            throw new ParameterValidationException(violations);
        }
    }


    private static Map<String, String> resolveOutputs(YamlModule module,
                                                       Map<String, String> paramScope) {
        if (module.outputs().isEmpty()) {
            return Map.of();
        }

        VariableResolver outputResolver = new VariableResolver(
                Map.of("var", (VariableSource) paramScope::get), Set.of());

        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, YamlModuleOutput> entry : module.outputs().entrySet()) {
            String outputName = entry.getKey();
            YamlModuleOutput output = entry.getValue();

            validateOutputTemplateScope(output.value(), outputName, module.name());

            String resolvedValue = outputResolver.resolveString(output.value(),
                    "output." + module.name() + "." + outputName);

            try {
                output.type().parse(resolvedValue);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Output '" + outputName + "' in module '" + module.name()
                        + "': resolved value '" + resolvedValue
                        + "' is not valid " + output.type(), e);
            }

            resolved.put(outputName, resolvedValue);
        }
        return Map.copyOf(resolved);
    }

    private static void validateOutputTemplateScope(String template, String outputName,
                                                     String moduleName) {
        Matcher matcher = VAR_REF.matcher(template);
        while (matcher.find()) {
            String key = matcher.group(1);
            int dot = key.indexOf('.');
            String prefix = dot >= 0 ? key.substring(0, dot) : key;
            if (!"var".equals(prefix)) {
                throw new IllegalArgumentException(
                        "Output '" + outputName + "' in module '" + moduleName
                        + "': template references '${" + key
                        + "}' — output templates may only use ${var.*} references.");
            }
        }
    }
}
