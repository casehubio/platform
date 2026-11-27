package io.casehub.template.registry;

import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TemplateSchema(
    String namespace,
    String templateName,
    List<String> patterns,
    Map<String, VariableConstraint> variables,
    List<StructuralSummary> structuralSummaries,
    MessageFormatSchema messageFormatSchema
) {
    public TemplateSchema(String namespace, String templateName,
                           List<String> patterns,
                           Map<String, VariableConstraint> variables,
                           List<StructuralSummary> structuralSummaries) {
        this(namespace, templateName, patterns, variables,
             structuralSummaries, null);
    }

    public TemplateSchema {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(variables, "variables");
        patterns = List.copyOf(patterns);
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
        structuralSummaries = structuralSummaries != null
            ? List.copyOf(structuralSummaries)
            : List.of();
    }

    public record StructuralSummary(
        String pattern,
        List<ElementSummary> elements
    ) {
        public StructuralSummary {
            Objects.requireNonNull(pattern, "pattern");
            elements = elements != null ? List.copyOf(elements) : List.of();
        }
    }

    public record ElementSummary(
        String kind,
        String surface,
        String lemma,
        String pos,
        String variableName
    ) {
        public ElementSummary {
            Objects.requireNonNull(kind, "kind");
        }
    }
}
