package io.casehub.template.registry.nlp;

import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.CandidateTrace;
import io.casehub.template.registry.InMemoryTemplateRegistry;
import io.casehub.template.registry.LanguageFamilyRegistry;
import io.casehub.template.registry.MatchTrace;
import io.casehub.template.registry.MatchTraceResult;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.NlpTokenizerFactory;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateFormatHelper;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.TemplateSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class NlpTemplateRegistry extends InMemoryTemplateRegistry {

    private final NlpTokenizerFactory tokenizerFactory;
    private final LanguageFamilyRegistry familyRegistry;
    private final Map<String, MatchingStrategy> strategies;
    private final double scoreThreshold;
    private final ConcurrentMap<String, NamespaceState> namespaceStates = new ConcurrentHashMap<>();

    private record NamespaceState(
        NlpTokenizer tokenizer,
        MatchingStrategy strategy,
        MatchingStrategy.Index index
    ) {}

    public NlpTemplateRegistry(NlpTokenizerFactory tokenizerFactory,
                               LanguageFamilyRegistry familyRegistry,
                               Map<String, MatchingStrategy> strategies,
                               double scoreThreshold) {
        this.tokenizerFactory = Objects.requireNonNull(tokenizerFactory, "tokenizerFactory");
        this.familyRegistry = Objects.requireNonNull(familyRegistry, "familyRegistry");
        this.strategies = Map.copyOf(strategies);
        this.scoreThreshold = scoreThreshold;
    }

    @Override
    public void register(TemplateNamespace namespace) {
        super.register(namespace);
        buildNamespaceState(namespace);
    }

    @Override
    public void deregister(String namespaceName) {
        namespaceStates.remove(namespaceName);
        super.deregister(namespaceName);
    }

    @Override
    public void replaceOrRegister(TemplateNamespace namespace) {
        super.replaceOrRegister(namespace);
        buildNamespaceState(namespace);
    }

    private void buildNamespaceState(TemplateNamespace namespace) {
        String lang = namespace.language() != null ? namespace.language() : "en";

        Optional<String> familyId = familyRegistry.familyFor(lang);
        if (familyId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Language '" + lang + "' is not registered in LanguageFamilyRegistry. "
                    + "Registered languages: " + familyRegistry.registeredLanguages());
        }

        MatchingStrategy strategy = strategies.get(familyId.get());
        if (strategy == null) {
            throw new IllegalArgumentException(
                    "No MatchingStrategy with familyId '" + familyId.get()
                    + "' for language '" + lang + "'. "
                    + "Available strategies: " + strategies.keySet());
        }

        NlpTokenizer tokenizer = tokenizerFactory.create(lang);
        MatchingStrategy.Index index = strategy.buildIndex(namespace, tokenizer);
        namespaceStates.put(namespace.name(), new NamespaceState(tokenizer, strategy, index));
    }

    @Override
    public TemplateMatch match(String input) {
        Objects.requireNonNull(input, "input");
        TemplateMatch.Matched bestMatch = null;
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

        if (bestMatch != null) return bestMatch;
        if (bestNearMiss != null) return bestNearMiss;
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

        NamespaceState state = namespaceStates.get(namespaceName);
        if (state == null) {
            return new TemplateMatch.Unmatched(input);
        }

        List<AnnotatedToken> inputTokens = state.tokenizer.tokenize(input);
        List<MatchingStrategy.PatternRef> candidates = state.strategy.selectCandidates(
                state.index, inputTokens);

        TemplateMatch.Matched bestMatch = null;
        String nearMissTemplate = null;
        String nearMissPattern = null;
        double nearMissScore = 0.0;

        for (MatchingStrategy.PatternRef ref : candidates) {
            Optional<MatchingStrategy.MatchResult> result = state.strategy.match(
                    state.index, ref, inputTokens);

            if (result.isPresent() && result.get().score() > scoreThreshold) {
                if (bestMatch == null || result.get().score() > bestMatch.score()) {
                    bestMatch = new TemplateMatch.Matched(
                            namespaceName, ref.templateName(), ref.patternIndex(),
                            result.get().fields(), result.get().score(), ns.version());
                }
            } else if (result.isPresent() && result.get().score() > nearMissScore) {
                nearMissScore = result.get().score();
                nearMissTemplate = ref.templateName();
                TemplateDefinition def = ns.templates().get(ref.templateName());
                if (def != null && ref.patternIndex() < def.patterns().size()) {
                    nearMissPattern = def.patterns().get(ref.patternIndex());
                }
            }
        }

        if (bestMatch != null) return bestMatch;
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

        NamespaceState state = namespaceStates.get(namespaceName);
        if (state == null) {
            return candidates;
        }

        List<AnnotatedToken> inputTokens = state.tokenizer.tokenize(input);
        candidates.addAll(state.strategy.traceAll(
                state.index, namespaceName, inputTokens, scoreThreshold));

        return candidates;
    }

    @Override
    public MatchTraceResult matchWithTrace(String input) {
        Objects.requireNonNull(input, "input");
        List<CandidateTrace> allCandidates = new ArrayList<>();
        TemplateMatch.Matched bestMatch = null;

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
        NamespaceState state = namespaceStates.get(namespaceName);
        List<TemplateSchema.StructuralSummary> summaries = state != null
                ? state.strategy.structuralSummaries(state.index, def.name())
                : List.of();
        return new TemplateSchema(namespaceName, def.name(),
                def.patterns(), def.variables(), summaries);
    }
}
