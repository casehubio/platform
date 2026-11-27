package io.casehub.template.registry.llm;

import io.casehub.template.registry.AdaptiveFallbackMatcher;
import io.casehub.template.registry.CandidateOutcome;
import io.casehub.template.registry.CandidateTrace;
import io.casehub.template.registry.ClassificationResult;
import io.casehub.template.registry.CollisionDetector;
import io.casehub.template.registry.MatchTrace;
import io.casehub.template.registry.MatchTraceResult;
import io.casehub.template.registry.NlpTokenizer;
import io.casehub.template.registry.TemplateCandidate;
import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.template.registry.nlp.NlpTemplateRegistry;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AdaptiveTemplateRegistry extends NlpTemplateRegistry {

    private static final Logger LOG = Logger.getLogger(
        AdaptiveTemplateRegistry.class.getName());

    private final AdaptiveFallbackMatcher fallbackMatcher;
    private final double confidenceThreshold;
    private final ClassificationCache classificationCache;

    public AdaptiveTemplateRegistry(NlpTokenizer tokenizer,
                                    AdaptiveFallbackMatcher fallbackMatcher,
                                    double confidenceThreshold,
                                    ClassificationCache classificationCache) {
        super(tokenizer);
        this.fallbackMatcher = fallbackMatcher;
        this.confidenceThreshold = confidenceThreshold;
        this.classificationCache = classificationCache;
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
                if (matched.score() == 1.0) return matched;
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
                namespaceName, matched.templateName(), -1, -1.0,
                CandidateOutcome.ADAPTIVE_MATCHED, List.of(), matched.score()));
            return new MatchTraceResult(adaptiveResult,
                new MatchTrace(candidates,
                    namespaceName + ":" + matched.templateName(),
                    matched.score()));
        }

        return new MatchTraceResult(adaptiveResult,
            new MatchTrace(candidates, null, 0.0));
    }

    @Override
    public void replaceOrRegister(TemplateNamespace namespace) {
        super.replaceOrRegister(namespace);
        classificationCache.invalidateNamespace(namespace.name());
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

        if (snapshotReplaceNamespace(namespaceName, expandedNs)) {
            recompileNlp(namespaceName, expandedNs);
            classificationCache.invalidateNamespace(namespaceName);
        }
    }
}
