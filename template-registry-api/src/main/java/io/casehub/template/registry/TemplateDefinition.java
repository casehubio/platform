package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TemplateDefinition(
    String name,
    List<String> patterns,
    Map<String, VariableConstraint> variables
) {
    public TemplateDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(patterns, "patterns");
        if (patterns.isEmpty()) {
            throw new IllegalArgumentException(
                "Template must have at least one pattern");
        }
        patterns = List.copyOf(patterns);
        variables = variables != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(variables))
            : Map.of();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private final List<String> patterns = new ArrayList<>();
        private final LinkedHashMap<String, VariableConstraint> variables = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder pattern(String pattern) {
            patterns.add(Objects.requireNonNull(pattern, "pattern"));
            return this;
        }

        public Builder variable(String name, VariableConstraint constraint) {
            variables.put(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(constraint, "constraint"));
            return this;
        }

        public TemplateDefinition build() {
            return new TemplateDefinition(name, patterns, variables);
        }
    }
}
