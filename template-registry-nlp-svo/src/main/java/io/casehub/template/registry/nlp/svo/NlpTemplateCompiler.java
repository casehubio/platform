package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class NlpTemplateCompiler {

    private NlpTemplateCompiler() {}

    public static CompiledNlpTemplate compile(TemplateDefinition definition,
                                               NlpTokenizer tokenizer,
                                               Set<String> stopWords) {
        List<StructuralMatcher> matchers = new ArrayList<>();
        List<Set<String>> keywordSets = new ArrayList<>();
        List<TemplateSchema.StructuralSummary> summaries = new ArrayList<>();

        for (String pattern : definition.patterns()) {
            StructuralMatcher matcher = StructuralMatcher.compile(pattern, tokenizer);
            matchers.add(matcher);
            keywordSets.add(LemmatisedKeywordFilter.extract(pattern, tokenizer, stopWords));

            List<TemplateSchema.ElementSummary> elementSummaries = new ArrayList<>();
            for (StructuralMatcher.Element elem : matcher.elements()) {
                elementSummaries.add(new TemplateSchema.ElementSummary(
                    elem.kind().name(),
                    elem.surface(),
                    elem.lemma(),
                    elem.pos(),
                    elem.variableName()));
            }
            summaries.add(new TemplateSchema.StructuralSummary(
                pattern, elementSummaries));
        }

        return new CompiledNlpTemplate(matchers, keywordSets, summaries);
    }

    public record CompiledNlpTemplate(
        List<StructuralMatcher> matchers,
        List<Set<String>> keywordSets,
        List<TemplateSchema.StructuralSummary> structuralSummaries
    ) {
        public CompiledNlpTemplate {
            matchers = List.copyOf(matchers);
            keywordSets = List.copyOf(keywordSets);
            structuralSummaries = List.copyOf(structuralSummaries);
        }
    }
}
