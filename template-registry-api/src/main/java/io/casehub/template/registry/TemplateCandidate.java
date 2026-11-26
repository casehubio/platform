package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TemplateCandidate(
    String templateName,
    List<String> patterns,
    Map<String, VariableConstraint> variables
) {
    public TemplateCandidate {
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(patterns, "patterns");
        patterns = List.copyOf(patterns);
        variables = variables != null ? Map.copyOf(variables) : Map.of();
    }

    public static TemplateCandidate from(TemplateDefinition def) {
        return new TemplateCandidate(
            def.name(), def.patterns(), def.variables());
    }
}
