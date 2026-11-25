package io.casehub.template.registry.nlp;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.CandidateTrace;
import io.casehub.template.registry.ElementTrace;
import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.MatchTrace;
import io.casehub.template.registry.MatchTraceResult;
import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateFormatHelper;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.TemplateSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NlpTemplateRegistry extends InMemoryTemplateRegistry {

    private static final double DEFAULT_AFFINITY_THRESHOLD = 0.3;
    private static final double DEFAULT_SCORE_THRESHOLD = 0.5;

    private final NlpTokenizer tokenizer;
    private final double affinityThreshold;
    private final double scoreThreshold;
    private final ConcurrentMap<String, NlpTemplateCompiler.CompiledNlpTemplate> nlpCompiled =
        new ConcurrentHashMap<>();

    public NlpTemplateRegistry(NlpTokenizer tokenizer) {
        this(tokenizer, DEFAULT_AFFINITY_THRESHOLD, DEFAULT_SCORE_THRESHOLD);
    }

    public NlpTemplateRegistry(NlpTokenizer tokenizer,
                                double affinityThreshold,
                                double scoreThreshold) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.affinityThreshold = affinityThreshold;
        this.scoreThreshold = scoreThreshold;
    }

    @Override
    public void register(TemplateNamespace namespace) {
        super.register(namespace);
        for (TemplateDefinition def : namespace.templates().values()) {
            String key = namespace.name() + ":" + def.name();
            nlpCompiled.put(key, NlpTemplateCompiler.compile(def, tokenizer));
        }
    }

    @Override
    public void deregister(String namespaceName) {
        for (String key : nlpCompiled.keySet()) {
            if (key.startsWith(namespaceName + ":")) {
                nlpCompiled.remove(key);
            }
        }
        super.deregister(namespaceName);
    }


    @Override
    public TemplateMatch match(String input) {
        Objects.requireNonNull(input, "input");
        TemplateMatch.Matched  bestMatch    = null;
        TemplateMatch.NearMiss bestNearMiss = null;

        for (String ns : namespaces()) {
            TemplateMatch result = match(ns, input);
            if (result instanceof TemplateMatch.Matched matched) {
                if (matched.score() == 1.0) {
                    return matched;
                }
                if (bestMatch == null || matched.score() > bestMatch.score()) {
                    bestMatch = matched;
                }
            } else if (result instanceof TemplateMatch.NearMiss nearMiss) {
                if (bestNearMiss == null) {
                    bestNearMiss = nearMiss;
                }
            }
        }

        if (bestMatch != null) {
            return bestMatch;
        }
        if (bestNearMiss != null) {
            return bestNearMiss;
        }
        return new TemplateMatch.Unmatched(input);
    }

    @Override
    protected TemplateMatch matchInNamespace(String namespaceName,
                                             TemplateNamespace ns,
                                             String input,
                                             Snapshot snap) {
        TemplateMatch regexResult = super.matchInNamespace(
                namespaceName, ns, input, snap);
        if (regexResult instanceof TemplateMatch.Matched) {
            return regexResult;
        }

        List<AnnotatedToken>  inputTokens      = tokenizer.tokenize(input);
        TemplateMatch.Matched bestMatch        = null;
        String                nearMissTemplate = null;
        String                nearMissPattern  = null;
        double                nearMissScore    = 0.0;

        for (var templateEntry : ns.templates().entrySet()) {
            TemplateDefinition                      def      = templateEntry.getValue();
            String                                  key      = namespaceName + ":" + def.name();
            NlpTemplateCompiler.CompiledNlpTemplate compiled = nlpCompiled.get(key);
            if (compiled == null) {continue;}

            for (int i = 0; i < compiled.matchers().size(); i++) {
                Set<String> keywords = compiled.keywordSets().get(i);
                double affinity = LemmatisedKeywordFilter.affinity(
                        keywords, inputTokens);
                if (affinity < affinityThreshold && !keywords.isEmpty()) {
                    continue;
                }

                StructuralMatcher matcher = compiled.matchers().get(i);
                var               result  = matcher.match(inputTokens, def.variables());
                if (result.isPresent() && result.get().score() > scoreThreshold) {
                    if (bestMatch == null
                        || result.get().score() > bestMatch.score()) {
                        bestMatch = new TemplateMatch.Matched(
                                namespaceName, def.name(), i,
                                result.get().fields(), result.get().score());
                    }
                } else if (result.isPresent() && result.get().score() > nearMissScore) {
                    nearMissScore    = result.get().score();
                    nearMissTemplate = def.name();
                    nearMissPattern  = def.patterns().get(i);
                }
            }
        }

        if (bestMatch != null) {
            return bestMatch;
        }
        if (nearMissTemplate != null) {
            return new TemplateMatch.NearMiss(
                    namespaceName, input, nearMissTemplate,
                    TemplateFormatHelper.formatSuggestion(nearMissPattern));
        }
        return new TemplateMatch.Unmatched(input);
    }

    @Override
    protected List<CandidateTrace> matchInNamespaceWithTrace(
            String namespaceName, TemplateNamespace ns,
            String input, Snapshot snap) {
        List<CandidateTrace> candidates = new ArrayList<>(
                super.matchInNamespaceWithTrace(namespaceName, ns, input, snap));

        List<AnnotatedToken> inputTokens = tokenizer.tokenize(input);

        for (var templateEntry : ns.templates().entrySet()) {
            TemplateDefinition                      def      = templateEntry.getValue();
            String                                  key      = namespaceName + ":" + def.name();
            NlpTemplateCompiler.CompiledNlpTemplate compiled = nlpCompiled.get(key);
            if (compiled == null) {continue;}

            for (int i = 0; i < compiled.matchers().size(); i++) {
                Set<String> keywords = compiled.keywordSets().get(i);
                double affinity = LemmatisedKeywordFilter.affinity(
                        keywords, inputTokens);
                if (affinity < affinityThreshold && !keywords.isEmpty()) {
                    candidates.add(new CandidateTrace(
                            namespaceName, def.name(), i, affinity,
                            CandidateOutcome.KEYWORD_FILTERED, List.of(), 0.0));
                    continue;
                }

                StructuralMatcher  matcher     = compiled.matchers().get(i);
                var                traceResult = matcher.matchWithTrace(inputTokens, def.variables());
                List<ElementTrace> elements    = traceResult.elements();

                if (traceResult.result().isPresent()
                    && traceResult.result().get().score() > scoreThreshold) {
                    double score = traceResult.result().get().score();
                    candidates.add(new CandidateTrace(
                            namespaceName, def.name(), i, affinity,
                            CandidateOutcome.MATCHED, elements, score));
                } else if (traceResult.result().isPresent()) {
                    candidates.add(new CandidateTrace(
                            namespaceName, def.name(), i, affinity,
                            CandidateOutcome.BELOW_THRESHOLD, elements,
                            traceResult.result().get().score()));
                } else {
                    candidates.add(new CandidateTrace(
                            namespaceName, def.name(), i, affinity,
                            CandidateOutcome.STRUCTURAL_FAILED, elements, 0.0));
                }
            }
        }
        return candidates;
    }

    @Override
    public MatchTraceResult matchWithTrace(String input) {
        Objects.requireNonNull(input, "input");
        List<CandidateTrace>  allCandidates = new ArrayList<>();
        TemplateMatch.Matched bestMatch     = null;

        for (String ns : namespaces()) {
            var nsResult = matchWithTrace(ns, input);
            allCandidates.addAll(nsResult.trace().candidates());
            if (nsResult.result() instanceof TemplateMatch.Matched matched) {
                if (matched.score() == 1.0) {
                    return new MatchTraceResult(matched,
                                                new MatchTrace(allCandidates,
                                                               matched.namespace() + ":" + matched.templateName(),
                                                               matched.score()));
                }
                if (bestMatch == null || matched.score() > bestMatch.score()) {
                    bestMatch = matched;
                }
            }
        }

        TemplateMatch result = bestMatch != null
                               ? bestMatch : new TemplateMatch.Unmatched(input);
        String bestCandidate = bestMatch != null
                               ? bestMatch.namespace() + ":" + bestMatch.templateName() : null;
        double bestScore = bestMatch != null ? bestMatch.score() : 0.0;
        return new MatchTraceResult(result,
                                    new MatchTrace(allCandidates, bestCandidate, bestScore));
    }


    @Override
    protected TemplateSchema buildSchema(String namespaceName,
                                          TemplateDefinition def) {
        String key = namespaceName + ":" + def.name();
        NlpTemplateCompiler.CompiledNlpTemplate compiled = nlpCompiled.get(key);
        List<TemplateSchema.StructuralSummary> summaries = compiled != null
            ? compiled.structuralSummaries()
            : List.of();
        return new TemplateSchema(namespaceName, def.name(),
            def.patterns(), def.variables(), summaries);
    }
}
