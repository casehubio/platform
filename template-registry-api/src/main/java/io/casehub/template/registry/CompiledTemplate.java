package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CompiledTemplate {

    private final String template;
    private final Pattern pattern;
    private final List<String> variableNames;
    private final Map<String, String> groupNameToVarName;

    CompiledTemplate(String template, Pattern pattern, List<String> variableNames,
                     Map<String, String> groupNameToVarName) {
        this.template = template;
        this.pattern = pattern;
        this.variableNames = variableNames;
        this.groupNameToVarName = groupNameToVarName;
    }

    public Optional<Map<String, String>> extract(String input) {
        return extract(input, Map.of());
    }

    public Optional<Map<String, String>> extract(String input,
                                                  Map<String, VariableConstraint> constraints) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(constraints, "constraints");

        Matcher matcher = pattern.matcher(input);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : groupNameToVarName.entrySet()) {
            String value = matcher.group(entry.getKey());
            if (value != null) {
                result.put(entry.getValue(), value);
            }
        }

        for (var entry : constraints.entrySet()) {
            String name = entry.getKey();
            if (!variableNames.contains(name)) {
                continue;
            }
            VariableConstraint constraint = entry.getValue();
            String value = result.get(name);

            if (value == null || value.isEmpty()) {
                if (constraint.required()) {
                    return Optional.empty();
                }
                continue;
            }

            if (constraint instanceof VariableConstraint.EnumConstraint ec) {
                if (ec.values().stream().noneMatch(v -> v.equalsIgnoreCase(value))) {
                    return Optional.empty();
                }
            }
        }

        return Optional.of(Collections.unmodifiableMap(result));
    }

    public String template() {
        return template;
    }

    public List<String> variableNames() {
        return variableNames;
    }
}
