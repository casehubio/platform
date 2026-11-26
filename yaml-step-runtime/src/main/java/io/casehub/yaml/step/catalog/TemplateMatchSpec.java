package io.casehub.yaml.step.catalog;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record TemplateMatchSpec(
    String namespace,
    String inputExpression,
    Map<String, List<ResolvedStep>> handlers,
    List<ResolvedStep> unmatchedHandler
) {
    public TemplateMatchSpec {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(inputExpression, "inputExpression");
        handlers = Map.copyOf(handlers);
        unmatchedHandler = unmatchedHandler != null ? List.copyOf(unmatchedHandler) : List.of();
    }
}
