package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.MatchingStrategy;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.Map;
import java.util.Set;

record SvoIndex(
    Map<String, SvoCompiledTemplate> compiledTemplates,
    Map<String, Map<String, VariableConstraint>> templateVariables,
    DiscriminationTrie trie,
    Set<String> stopWords,
    double affinityThreshold,
    int trieActivationThreshold
) implements MatchingStrategy.Index {

    SvoIndex {
        compiledTemplates = Map.copyOf(compiledTemplates);
        templateVariables = Map.copyOf(templateVariables);
    }
}
