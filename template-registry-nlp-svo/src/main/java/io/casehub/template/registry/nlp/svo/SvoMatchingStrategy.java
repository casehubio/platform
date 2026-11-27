package io.casehub.template.registry.nlp.svo;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.CandidateTrace;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.TemplateSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SvoMatchingStrategy implements MatchingStrategy {

    private static final double DEFAULT_AFFINITY_THRESHOLD = 0.3;
    private static final int DEFAULT_TRIE_THRESHOLD = 100;

    private final double affinityThreshold;
    private final int trieActivationThreshold;

    public SvoMatchingStrategy() {
        this(DEFAULT_AFFINITY_THRESHOLD, DEFAULT_TRIE_THRESHOLD);
    }

    public SvoMatchingStrategy(double affinityThreshold, int trieActivationThreshold) {
        this.affinityThreshold = affinityThreshold;
        this.trieActivationThreshold = trieActivationThreshold;
    }

    @Override
    public String familyId() {
        return "svo";
    }

    @Override
    public Index buildIndex(TemplateNamespace namespace, NlpTokenizer tokenizer) {
        String lang = namespace.language() != null ? namespace.language() : "en";
        Set<String> stopWords = SvoStopWords.forLanguage(lang);

        Map<String, SvoCompiledTemplate> compiled = new LinkedHashMap<>();
        Map<String, Map<String, io.casehub.yaml.core.resolver.VariableConstraint>> variables = new LinkedHashMap<>();

        for (TemplateDefinition def : namespace.templates().values()) {
            compiled.put(def.name(), compileTemplate(def, tokenizer, stopWords));
            variables.put(def.name(), def.variables());
        }

        int totalPatterns = compiled.values().stream()
                .mapToInt(c -> c.matchers().size()).sum();
        DiscriminationTrie trie = null;
        if (totalPatterns >= trieActivationThreshold) {
            Map<String, NlpTemplateCompiler.CompiledNlpTemplate> legacyMap = new LinkedHashMap<>();
            String prefix = namespace.name() + ":";
            for (var entry : compiled.entrySet()) {
                legacyMap.put(prefix + entry.getKey(),
                        new NlpTemplateCompiler.CompiledNlpTemplate(
                                entry.getValue().matchers(),
                                entry.getValue().keywordSets(),
                                entry.getValue().structuralSummaries()));
            }
            trie = DiscriminationTrie.build(legacyMap, namespace.name());
        }

        return new SvoIndex(compiled, variables, trie, stopWords,
                affinityThreshold, trieActivationThreshold);
    }

    private SvoCompiledTemplate compileTemplate(TemplateDefinition def,
                                                 NlpTokenizer tokenizer,
                                                 Set<String> stopWords) {
        List<StructuralMatcher> matchers = new ArrayList<>();
        List<Set<String>> keywordSets = new ArrayList<>();
        List<TemplateSchema.StructuralSummary> summaries = new ArrayList<>();

        for (String pattern : def.patterns()) {
            StructuralMatcher matcher = StructuralMatcher.compile(pattern, tokenizer);
            matchers.add(matcher);
            keywordSets.add(LemmatisedKeywordFilter.extract(pattern, tokenizer, stopWords));

            List<TemplateSchema.ElementSummary> elementSummaries = new ArrayList<>();
            for (StructuralMatcher.Element elem : matcher.elements()) {
                elementSummaries.add(new TemplateSchema.ElementSummary(
                        elem.kind().name(), elem.surface(), elem.lemma(),
                        elem.pos(), elem.variableName()));
            }
            summaries.add(new TemplateSchema.StructuralSummary(pattern, elementSummaries));
        }

        return new SvoCompiledTemplate(matchers, keywordSets, summaries);
    }

    @Override
    public List<PatternRef> selectCandidates(Index index,
                                              List<AnnotatedToken> inputTokens) {
        SvoIndex svoIndex = (SvoIndex) index;
        if (svoIndex.trie() != null) {
            return svoIndex.trie().walk(inputTokens).stream()
                    .map(ref -> new PatternRef(ref.templateName(), ref.patternIndex()))
                    .toList();
        }

        List<PatternRef> candidates = new ArrayList<>();
        for (var entry : svoIndex.compiledTemplates().entrySet()) {
            SvoCompiledTemplate compiled = entry.getValue();
            for (int i = 0; i < compiled.matchers().size(); i++) {
                Set<String> keywords = compiled.keywordSets().get(i);
                double affinity = LemmatisedKeywordFilter.affinity(
                        keywords, inputTokens, svoIndex.stopWords());
                if (affinity >= svoIndex.affinityThreshold() || keywords.isEmpty()) {
                    candidates.add(new PatternRef(entry.getKey(), i));
                }
            }
        }
        return candidates;
    }

    @Override
    public Optional<MatchResult> match(Index index, PatternRef candidate,
                                        List<AnnotatedToken> inputTokens) {
        SvoIndex svoIndex = (SvoIndex) index;
        SvoCompiledTemplate compiled = svoIndex.compiledTemplates().get(candidate.templateName());
        if (compiled == null || candidate.patternIndex() >= compiled.matchers().size()) {
            return Optional.empty();
        }

        StructuralMatcher matcher = compiled.matchers().get(candidate.patternIndex());
        var constraints = svoIndex.templateVariables()
                .getOrDefault(candidate.templateName(), Map.of());
        return matcher.match(inputTokens, constraints)
                .map(r -> new MatchResult(r.fields(), r.score()));
    }

    @Override
    public TraceableMatchResult matchWithTrace(Index index, PatternRef candidate,
                                                List<AnnotatedToken> inputTokens) {
        SvoIndex svoIndex = (SvoIndex) index;
        SvoCompiledTemplate compiled = svoIndex.compiledTemplates().get(candidate.templateName());
        if (compiled == null || candidate.patternIndex() >= compiled.matchers().size()) {
            return new TraceableMatchResult(Optional.empty(), List.of());
        }

        StructuralMatcher matcher = compiled.matchers().get(candidate.patternIndex());
        var constraints = svoIndex.templateVariables()
                .getOrDefault(candidate.templateName(), Map.of());
        var traceResult = matcher.matchWithTrace(inputTokens, constraints);
        Optional<MatchResult> result = traceResult.result()
                .map(r -> new MatchResult(r.fields(), r.score()));
        return new TraceableMatchResult(result, traceResult.elements());
    }

    @Override
    public List<CandidateTrace> traceAll(Index index, String namespaceName,
                                          List<AnnotatedToken> inputTokens,
                                          double scoreThreshold) {
        SvoIndex svoIndex = (SvoIndex) index;
        List<CandidateTrace> traces = new ArrayList<>();

        for (var entry : svoIndex.compiledTemplates().entrySet()) {
            SvoCompiledTemplate compiled = entry.getValue();
            for (int i = 0; i < compiled.matchers().size(); i++) {
                Set<String> keywords = compiled.keywordSets().get(i);
                double affinity = LemmatisedKeywordFilter.affinity(
                        keywords, inputTokens, svoIndex.stopWords());

                if (affinity < svoIndex.affinityThreshold() && !keywords.isEmpty()) {
                    traces.add(new CandidateTrace(namespaceName, entry.getKey(), i,
                            affinity, -1.0, CandidateOutcome.KEYWORD_FILTERED,
                            List.of(), 0.0));
                    continue;
                }

                StructuralMatcher matcher = compiled.matchers().get(i);
                var constraints = svoIndex.templateVariables()
                        .getOrDefault(entry.getKey(), Map.of());
                var traceResult = matcher.matchWithTrace(inputTokens, constraints);

                if (traceResult.result().isPresent()
                        && traceResult.result().get().score() > scoreThreshold) {
                    traces.add(new CandidateTrace(namespaceName, entry.getKey(), i,
                            affinity, -1.0, CandidateOutcome.MATCHED,
                            traceResult.elements(), traceResult.result().get().score()));
                } else if (traceResult.result().isPresent()) {
                    traces.add(new CandidateTrace(namespaceName, entry.getKey(), i,
                            affinity, -1.0, CandidateOutcome.BELOW_THRESHOLD,
                            traceResult.elements(), traceResult.result().get().score()));
                } else {
                    traces.add(new CandidateTrace(namespaceName, entry.getKey(), i,
                            affinity, -1.0, CandidateOutcome.STRUCTURAL_FAILED,
                            traceResult.elements(), 0.0));
                }
            }
        }
        return traces;
    }

    @Override
    public List<TemplateSchema.StructuralSummary> structuralSummaries(
            Index index, String templateName) {
        SvoIndex svoIndex = (SvoIndex) index;
        SvoCompiledTemplate compiled = svoIndex.compiledTemplates().get(templateName);
        return compiled != null ? compiled.structuralSummaries() : List.of();
    }
}
