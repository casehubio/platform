package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.TemplateSchema;

import java.util.List;
import java.util.Set;

record SvoCompiledTemplate(
    List<StructuralMatcher> matchers,
    List<Set<String>> keywordSets,
    List<TemplateSchema.StructuralSummary> structuralSummaries
) {
    SvoCompiledTemplate {
        matchers = List.copyOf(matchers);
        keywordSets = List.copyOf(keywordSets);
        structuralSummaries = List.copyOf(structuralSummaries);
    }
}
