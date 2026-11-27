package io.casehub.template.registry.llm;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import io.casehub.template.registry.AdaptiveFallbackMatcher;
import io.casehub.template.registry.AnnotatedToken;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.CandidateTrace;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.CollisionDetector;
import io.casehub.template.registry.ElementTrace;
import io.casehub.template.registry.MatchTrace;
import io.casehub.template.registry.MatchTraceResult;
import io.casehub.template.registry.LanguageFamilyRegistry;
import io.casehub.template.registry.MatchingStrategy;
import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.NlpTokenizerFactory;
import io.casehub.template.registry.TemplateCandidate;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateFormatHelper;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.NlpTemplateRegistry;
import io.casehub.template.registry.nlp.svo.LemmatisedKeywordFilter;
import io.casehub.template.registry.nlp.svo.NlpTemplateCompiler;
import io.casehub.template.registry.nlp.svo.StructuralMatcher;
import io.casehub.template.registry.nlp.svo.SvoStopWords;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdaptiveTemplateRegistry extends NlpTemplateRegistry {

    private static final Logger LOG = Logger.getLogger(
            AdaptiveTemplateRegistry.class.getName());
    private static final double SYNONYM_LEARNING_THRESHOLD = 0.7;
    private static final double DEFAULT_AFFINITY_THRESHOLD = 0.3;

    private final NlpTokenizerFactory                                        tokenizerFactory;
    private final double                                                     localScoreThreshold;
    private final double                                                     affinityThreshold;
    private final AdaptiveFallbackMatcher                                    fallbackMatcher;
    private final double                                                     confidenceThreshold;
    private final ClassificationCache                                        classificationCache;
    private final EmbeddingModel                                             embeddingModel;
    private final double                                                     embeddingSimilarityThreshold;
    private final ConcurrentMap<String, NlpTemplateCompiler.CompiledNlpTemplate> nlpCompiled = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, NlpTokenizer>                        namespaceTokenizers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, float[]>                             templateEmbeddings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<String, Set<String>>>  synonyms           = new ConcurrentHashMap<>();

    public AdaptiveTemplateRegistry(NlpTokenizerFactory tokenizerFactory,
                                    LanguageFamilyRegistry familyRegistry,
                                    Map<String, MatchingStrategy> strategies,
                                    double scoreThreshold,
                                    AdaptiveFallbackMatcher fallbackMatcher,
                                    double confidenceThreshold,
                                    ClassificationCache classificationCache) {
        this(tokenizerFactory, familyRegistry, strategies, scoreThreshold,
             fallbackMatcher, confidenceThreshold, classificationCache, null, 0.5);
    }

    public AdaptiveTemplateRegistry(NlpTokenizerFactory tokenizerFactory,
                                    LanguageFamilyRegistry familyRegistry,
                                    Map<String, MatchingStrategy> strategies,
                                    double scoreThreshold,
                                    AdaptiveFallbackMatcher fallbackMatcher,
                                    double confidenceThreshold,
                                    ClassificationCache classificationCache,
                                    EmbeddingModel embeddingModel,
                                    double embeddingSimilarityThreshold) {
        super(tokenizerFactory, familyRegistry, strategies, scoreThreshold);
        this.tokenizerFactory             = tokenizerFactory;
        this.localScoreThreshold          = scoreThreshold;
        this.affinityThreshold            = DEFAULT_AFFINITY_THRESHOLD;
        this.fallbackMatcher              = fallbackMatcher;
        this.confidenceThreshold          = confidenceThreshold;
        this.classificationCache          = classificationCache;
        this.embeddingModel               = embeddingModel;
        this.embeddingSimilarityThreshold = embeddingSimilarityThreshold;
    }

    @Override
    public void register(TemplateNamespace namespace) {
        super.register(namespace);
        compileAndStore(namespace);
        computeTemplateEmbeddings(namespace);
    }

    @Override
    public void deregister(String namespaceName) {
        templateEmbeddings.keySet().removeIf(k -> k.startsWith(namespaceName + ":"));
        nlpCompiled.keySet().removeIf(k -> k.startsWith(namespaceName + ":"));
        namespaceTokenizers.remove(namespaceName);
        synonyms.remove(namespaceName);
        super.deregister(namespaceName);
    }

    @Override
    public void replaceOrRegister(TemplateNamespace namespace) {
        super.replaceOrRegister(namespace);
        nlpCompiled.keySet().removeIf(k -> k.startsWith(namespace.name() + ":"));
        compileAndStore(namespace);
        templateEmbeddings.keySet().removeIf(k -> k.startsWith(namespace.name() + ":"));
        computeTemplateEmbeddings(namespace);
        synonyms.remove(namespace.name());
        classificationCache.invalidateNamespace(namespace.name());
    }

    private void compileAndStore(TemplateNamespace namespace) {
        String lang = namespace.language() != null ? namespace.language() : "en";
        NlpTokenizer tokenizer = tokenizerFactory.create(lang);
        namespaceTokenizers.put(namespace.name(), tokenizer);
        Set<String> stopWords = SvoStopWords.forLanguage(lang);
        for (TemplateDefinition def : namespace.templates().values()) {
            String key = namespace.name() + ":" + def.name();
            nlpCompiled.put(key, NlpTemplateCompiler.compile(def, tokenizer, stopWords));
        }
    }

    private NlpTokenizer tokenizerFor(String namespaceName) {
        NlpTokenizer tok = namespaceTokenizers.get(namespaceName);
        return tok != null ? tok : tokenizerFactory.create("en");
    }

    private Set<String> stopWordsFor(TemplateNamespace ns) {
        String lang = ns.language() != null ? ns.language() : "en";
        return SvoStopWords.forLanguage(lang);
    }

    @Override
    protected TemplateMatch matchInNamespace(String namespaceName,
                                             TemplateNamespace ns,
                                             String input,
                                             Snapshot snap) {
        TemplateMatch regexResult = super.matchInNamespace(namespaceName, ns, input, snap);
        if (regexResult instanceof TemplateMatch.Matched) {
            return regexResult;
        }
        if (!(regexResult instanceof TemplateMatch.Unmatched)) {
            return regexResult;
        }

        NlpTokenizer                       tokenizer         = tokenizerFor(namespaceName);
        List<AnnotatedToken>               inputTokens       = tokenizer.tokenize(input);
        TemplateMatch.Matched              bestMatch         = null;
        String                             nearMissTemplate  = null;
        String                             nearMissPattern   = null;
        double                             nearMissScore     = 0.0;
        float[]                            inputEmbedding    = null;
        ConcurrentMap<String, Set<String>> namespaceSynonyms = synonyms.get(namespaceName);
        Set<String>                        stopWords         = stopWordsFor(ns);

        for (var templateEntry : ns.templates().entrySet()) {
            TemplateDefinition                      def      = templateEntry.getValue();
            String                                  key      = namespaceName + ":" + def.name();
            NlpTemplateCompiler.CompiledNlpTemplate compiled = nlpCompiled.get(key);
            if (compiled == null) {continue;}

            for (int i = 0; i < compiled.matchers().size(); i++) {
                Set<String> keywords = compiled.keywordSets().get(i);
                double affinity = namespaceSynonyms != null
                                  ? LemmatisedKeywordFilter.affinity(keywords, inputTokens, namespaceSynonyms)
                                  : LemmatisedKeywordFilter.affinity(keywords, inputTokens, stopWords);

                if (affinity < affinityThreshold && !keywords.isEmpty()) {
                    if (embeddingModel != null) {
                        try {
                            if (inputEmbedding == null) {
                                inputEmbedding = computeInputEmbedding(inputTokens);
                            }
                            String  embKey      = namespaceName + ":" + def.name() + ":" + i;
                            float[] templateEmb = templateEmbeddings.get(embKey);
                            if (templateEmb != null && inputEmbedding != null) {
                                double similarity = CosineSimilarity.between(Embedding.from(templateEmb), Embedding.from(inputEmbedding));
                                if (similarity >= embeddingSimilarityThreshold) {
                                    StructuralMatcher matcher = compiled.matchers().get(i);
                                    var               result  = matcher.match(inputTokens, def.variables());
                                    if (result.isPresent() && result.get().score() > localScoreThreshold) {
                                        learnSynonyms(namespaceName, keywords, inputTokens);
                                        if (bestMatch == null || result.get().score() > bestMatch.score()) {
                                            bestMatch = new TemplateMatch.Matched(
                                                    namespaceName, def.name(), i,
                                                    result.get().fields(), result.get().score(),
                                                    ns.version());
                                        }
                                    } else if (result.isPresent() && result.get().score() > nearMissScore) {
                                        nearMissScore    = result.get().score();
                                        nearMissTemplate = def.name();
                                        nearMissPattern  = def.patterns().get(i);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOG.log(Level.WARNING, "Embedding rescue failed for namespace " + namespaceName, e);
                        }
                    }
                    continue;
                }

                StructuralMatcher matcher = compiled.matchers().get(i);
                var               result  = matcher.match(inputTokens, def.variables());
                if (result.isPresent() && result.get().score() > localScoreThreshold) {
                    if (bestMatch == null || result.get().score() > bestMatch.score()) {
                        bestMatch = new TemplateMatch.Matched(
                                namespaceName, def.name(), i,
                                result.get().fields(), result.get().score(),
                                ns.version());
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
    public TemplateMatch match(String input) {
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
    public TemplateMatch match(String namespaceName, String input) {
        TemplateMatch result = super.match(namespaceName, input);
        if (result instanceof TemplateMatch.Matched) {
            return result;
        }
        return adaptiveFallback(namespaceName, input);
    }

    @Override
    public TemplateMatch matchStructural(String input) {
        TemplateMatch.Matched bestMatch = null;
        for (String ns : namespaces()) {
            TemplateMatch result = super.match(ns, input);
            if (result instanceof TemplateMatch.Matched matched) {
                if (matched.score() == 1.0) {return matched;}
                if (bestMatch == null || matched.score() > bestMatch.score()) {
                    bestMatch = matched;
                }
            }
        }
        return bestMatch != null ? bestMatch : new TemplateMatch.Unmatched(input);
    }

    @Override
    public MatchTraceResult matchWithTrace(String namespaceName, String input) {
        MatchTraceResult structuralResult = super.matchWithTrace(namespaceName, input);
        if (structuralResult.result() instanceof TemplateMatch.Matched) {
            return structuralResult;
        }

        TemplateMatch adaptiveResult = adaptiveFallback(namespaceName, input);
        List<CandidateTrace> candidates = new ArrayList<>(
                structuralResult.trace().candidates());

        if (adaptiveResult instanceof TemplateMatch.Matched matched) {
            candidates.add(new CandidateTrace(
                    namespaceName, matched.templateName(), -1, -1.0, -1.0, CandidateOutcome.ADAPTIVE_MATCHED, List.of(), matched.score()));
            return new MatchTraceResult(adaptiveResult,
                                        new MatchTrace(candidates,
                                                       namespaceName + ":" + matched.templateName(),
                                                       matched.score()));
        }

        return new MatchTraceResult(adaptiveResult,
                                    new MatchTrace(candidates, null, 0.0));
    }


    @Override
    protected List<CandidateTrace> matchInNamespaceWithTrace(
            String namespaceName, TemplateNamespace ns,
            String input, Snapshot snap) {
        List<CandidateTrace> candidates = new java.util.ArrayList<>(
                super.matchInNamespaceWithTrace(namespaceName, ns, input, snap));

        if (embeddingModel == null) {return candidates;}

        NlpTokenizer                       tokenizer         = tokenizerFor(namespaceName);
        List<AnnotatedToken>               inputTokens       = tokenizer.tokenize(input);
        float[]                            inputEmbedding    = null;
        ConcurrentMap<String, Set<String>> namespaceSynonyms = synonyms.get(namespaceName);
        Set<String>                        stopWords         = stopWordsFor(ns);

        for (var templateEntry : ns.templates().entrySet()) {
            TemplateDefinition                      def      = templateEntry.getValue();
            String                                  key      = namespaceName + ":" + def.name();
            NlpTemplateCompiler.CompiledNlpTemplate compiled = nlpCompiled.get(key);
            if (compiled == null) {continue;}

            for (int i = 0; i < compiled.matchers().size(); i++) {
                Set<String> keywords = compiled.keywordSets().get(i);
                double affinity = namespaceSynonyms != null
                                  ? LemmatisedKeywordFilter.affinity(keywords, inputTokens, namespaceSynonyms)
                                  : LemmatisedKeywordFilter.affinity(keywords, inputTokens, stopWords);

                if (affinity < affinityThreshold && !keywords.isEmpty()) {
                    try {
                        if (inputEmbedding == null) {
                            inputEmbedding = computeInputEmbedding(inputTokens);
                        }
                        String  embKey      = namespaceName + ":" + def.name() + ":" + i;
                        float[] templateEmb = templateEmbeddings.get(embKey);
                        if (templateEmb != null && inputEmbedding != null) {
                            double similarity = CosineSimilarity.between(
                                    Embedding.from(templateEmb), Embedding.from(inputEmbedding));
                            if (similarity >= embeddingSimilarityThreshold) {
                                StructuralMatcher  matcher     = compiled.matchers().get(i);
                                var                traceResult = matcher.matchWithTrace(inputTokens, def.variables());
                                List<ElementTrace> elements    = traceResult.elements();
                                if (traceResult.result().isPresent()
                                    && traceResult.result().get().score() > localScoreThreshold) {
                                    double score = traceResult.result().get().score();
                                    candidates.add(new CandidateTrace(
                                            namespaceName, def.name(), i, affinity, similarity,
                                            CandidateOutcome.EMBEDDING_RESCUED, elements, score));
                                } else {
                                    candidates.add(new CandidateTrace(
                                            namespaceName, def.name(), i, affinity, similarity,
                                            CandidateOutcome.STRUCTURAL_FAILED, List.of(), 0.0));
                                }
                            } else {
                                candidates.add(new CandidateTrace(
                                        namespaceName, def.name(), i, affinity, similarity,
                                        CandidateOutcome.EMBEDDING_FILTERED, List.of(), 0.0));
                            }
                        }
                    } catch (Exception e) {
                        LOG.log(Level.WARNING, "Embedding trace failed for namespace " + namespaceName, e);
                    }
                }
            }
        }
        return candidates;
    }

    private void computeTemplateEmbeddings(TemplateNamespace namespace) {
        if (embeddingModel == null) {return;}
        for (var templateEntry : namespace.templates().entrySet()) {
            TemplateDefinition                      def      = templateEntry.getValue();
            String                                  key      = namespace.name() + ":" + def.name();
            NlpTemplateCompiler.CompiledNlpTemplate compiled = nlpCompiled.get(key);
            if (compiled == null) {continue;}

            for (int i = 0; i < compiled.keywordSets().size(); i++) {
                Set<String> keywords = compiled.keywordSets().get(i);
                if (keywords.isEmpty()) {continue;}
                try {
                    String  phrase = String.join(" ", keywords);
                    float[] vec    = embeddingModel.embed(phrase).content().vector();
                    templateEmbeddings.put(key + ":" + i, vec);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to embed template " + key + " pattern " + i, e);
                }
            }
        }
    }

    private float[] computeInputEmbedding(List<AnnotatedToken> inputTokens) {
        Set<String> inputLemmas = new LinkedHashSet<>();
        for (AnnotatedToken token : inputTokens) {
            String lemma = token.lemma().toLowerCase();
            if (!lemma.isBlank()) {
                inputLemmas.add(lemma);
            }
        }
        if (inputLemmas.isEmpty()) {return null;}
        String phrase = String.join(" ", inputLemmas);
        return embeddingModel.embed(phrase).content().vector();
    }

    ConcurrentMap<String, ConcurrentMap<String, Set<String>>> synonyms() {
        return synonyms;
    }


    private void learnSynonyms(String namespaceName, Set<String> templateKeywords,
                               List<AnnotatedToken> inputTokens) {
        if (embeddingModel == null) {return;}

        Set<String> inputLemmas = new LinkedHashSet<>();
        for (AnnotatedToken t : inputTokens) {
            String lemma = t.lemma().toLowerCase();
            if (!lemma.isBlank()) {inputLemmas.add(lemma);}
        }

        List<String> missing = new java.util.ArrayList<>();
        for (String kw : templateKeywords) {
            if (!inputLemmas.contains(kw)) {missing.add(kw);}
        }
        if (missing.isEmpty()) {return;}

        List<String> inputLemmaList = new java.util.ArrayList<>(inputLemmas);
        try {
            var missingEmbeddings = embeddingModel.embedAll(
                    missing.stream().map(TextSegment::from).toList()).content();
            var inputEmbeddings = embeddingModel.embedAll(
                    inputLemmaList.stream().map(TextSegment::from).toList()).content();

            var namespaceSynonyms = synonyms.computeIfAbsent(namespaceName,
                                                             k -> new ConcurrentHashMap<>());

            for (int m = 0; m < missing.size(); m++) {
                float[] mVec      = missingEmbeddings.get(m).vector();
                double  bestSim   = 0.0;
                String  bestLemma = null;
                for (int j = 0; j < inputLemmaList.size(); j++) {
                    double sim = CosineSimilarity.between(
                            Embedding.from(mVec), inputEmbeddings.get(j));
                    if (sim > bestSim) {
                        bestSim   = sim;
                        bestLemma = inputLemmaList.get(j);
                    }
                }
                if (bestSim >= SYNONYM_LEARNING_THRESHOLD && bestLemma != null) {
                    namespaceSynonyms.computeIfAbsent(missing.get(m),
                                                      k -> ConcurrentHashMap.newKeySet()).add(bestLemma);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Synonym learning failed for namespace " + namespaceName, e);
        }
    }

    private TemplateMatch adaptiveFallback(String namespaceName, String input) {
        var                       cacheKey = new ClassificationCache.CacheKey(namespaceName, input);
        ClassificationCache.Entry cached   = classificationCache.get(cacheKey);
        if (cached != null) {
            TemplateMatch cachedMatch = cached.toTemplateMatch(namespaceName);
            return cachedMatch != null ? cachedMatch
                                       : new TemplateMatch.Unmatched(input);
        }

        TemplateNamespace ns = getNamespaceFromSnapshot(namespaceName);
        if (ns == null) {
            return new TemplateMatch.Unmatched(input);
        }

        List<TemplateCandidate> candidates = ns.templates().values()
                                               .stream().map(TemplateCandidate::from).toList();

        ClassificationResult cr;
        try {
            cr = fallbackMatcher.classify(input, candidates, ns.language());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Adaptive fallback failed for input in namespace "
                                   + namespaceName, e);
            return new TemplateMatch.Unmatched(input);
        }

        if (cr instanceof ClassificationResult.Matched matched) {
            if (!ns.templates().containsKey(matched.templateName())) {
                LOG.log(Level.WARNING, "LLM returned unknown template name ''{0}'' in namespace {1}",
                        new Object[]{matched.templateName(), namespaceName});
                classificationCache.putNegative(cacheKey);
                return new TemplateMatch.Unmatched(input);
            }

            classificationCache.putPositive(cacheKey, matched);

            if (matched.confidence() >= confidenceThreshold) {
                tryRegisterVariation(namespaceName, matched);
            }

            return new TemplateMatch.Matched(
                    namespaceName, matched.templateName(), -1,
                    matched.fields(), 0.9, ns.version());
        }

        classificationCache.putNegative(cacheKey);
        return new TemplateMatch.Unmatched(input);
    }

    private void tryRegisterVariation(String namespaceName,
                                      ClassificationResult.Matched matched) {
        String variation = matched.suggestedVariation();

        TemplateNamespace ns = getNamespaceFromSnapshot(namespaceName);
        if (ns == null) {return;}
        TemplateDefinition def = ns.templates().get(matched.templateName());
        if (def == null) {return;}

        if (!preservesAllVariables(variation, def.variables())) {
            return;
        }

        List<CollisionDetector.Collision> collisions =
                CollisionDetector.detect(
                        List.of(variation), def.variables(),
                        namespaceName, matched.templateName(), this);

        if (collisions.isEmpty()) {
            registerLearnedVariation(namespaceName, matched.templateName(), variation);
        } else {
            LOG.log(Level.WARNING, "Variation ''{0}'' collides with {1}:{2} — not registered",
                    new Object[]{variation, collisions.get(0).collidingNamespace(),
                                 collisions.get(0).collidingTemplate()});
        }
    }

    private boolean preservesAllVariables(
            String variation, Map<String, VariableConstraint> variables) {
        for (String varName : variables.keySet()) {
            if (!variation.contains("${" + varName + "}")) {
                return false;
            }
        }
        return true;
    }

    private void registerLearnedVariation(
            String namespaceName, String templateName, String variation) {
        TemplateNamespace ns = getNamespaceFromSnapshot(namespaceName);
        if (ns == null) {return;}
        TemplateDefinition def = ns.templates().get(templateName);
        if (def == null || def.patterns().contains(variation)) {return;}

        List<String> expanded = new ArrayList<>(def.patterns());
        expanded.add(variation);
        TemplateDefinition expandedDef =
                new TemplateDefinition(def.name(), expanded, def.variables());
        Map<String, TemplateDefinition> expandedTemplates =
                new LinkedHashMap<>(ns.templates());
        expandedTemplates.put(def.name(), expandedDef);
        TemplateNamespace expandedNs = new TemplateNamespace(
                ns.name(), expandedTemplates, ns.language());

        replaceOrRegister(expandedNs);
    }
}
