package io.casehub.template.registry;

import java.util.List;
import java.util.Objects;

public record CandidateTrace(
        String namespace,
        String templateName,
        int patternIndex,
        double keywordAffinity,
        double embeddingSimilarity,
        CandidateOutcome outcome,
        List<ElementTrace> elements,
        double score
) {
    public CandidateTrace {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(templateName, "templateName");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(elements, "elements");
        elements = List.copyOf(elements);
    }
}
