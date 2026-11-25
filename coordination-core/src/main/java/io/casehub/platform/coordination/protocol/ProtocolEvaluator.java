package io.casehub.platform.coordination.protocol;

import io.casehub.yaml.core.resolver.TemplateRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ProtocolEvaluator {

    static final double DEFAULT_AFFINITY_THRESHOLD = 0.3;

    private final ConcurrentMap<String, TemplateRegistry> protocols = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, String>> templateStrings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Set<String>>> templateKeywordCache = new ConcurrentHashMap<>();
    private final NlpProtocolMatcher nlpMatcher;
    private final double affinityThreshold;

    public ProtocolEvaluator() {
        this(null);
    }

    public ProtocolEvaluator(NlpProtocolMatcher nlpMatcher) {
        this(nlpMatcher, DEFAULT_AFFINITY_THRESHOLD);
    }

    public ProtocolEvaluator(NlpProtocolMatcher nlpMatcher, double affinityThreshold) {
        this.nlpMatcher = nlpMatcher;
        this.affinityThreshold = affinityThreshold;
    }

    public void register(ProtocolDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        TemplateRegistry.Builder builder = TemplateRegistry.builder();
        definition.templates().forEach(builder::add);
        TemplateRegistry registry = builder.build();
        if (protocols.putIfAbsent(definition.name(), registry) != null) {
            throw new IllegalArgumentException(
                    "Protocol already registered: " + definition.name());
        }
        templateStrings.put(definition.name(), definition.templates());
        Map<String, Set<String>> keywordMap = new LinkedHashMap<>();
        definition.templates().forEach((name, template) ->
                keywordMap.put(name, TemplateKeywords.extract(template)));
        templateKeywordCache.put(definition.name(), Map.copyOf(keywordMap));
    }

    public ProtocolMatchResult evaluate(ProtocolInstanceKey key, String response) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(response, "response");
        TemplateRegistry registry = protocols.get(key.protocolName());
        if (registry == null) {
            throw new IllegalArgumentException(
                    "Unknown protocol: " + key.protocolName());
        }
        Optional<TemplateRegistry.Match> match = registry.match(response);
        if (match.isPresent()) {
            return new ProtocolMatchResult.Matched(match.get().name(), match.get().values());
        }

        if (nlpMatcher != null) {
            Map<String, String> templates = templateStrings.get(key.protocolName());
            Map<String, Set<String>> keywordMap = templateKeywordCache.get(key.protocolName());
            Map<String, String> scopedTemplates = scopeCandidates(templates, keywordMap, response);
            if (!scopedTemplates.isEmpty()) {
                Optional<NlpProtocolMatcher.NlpMatch> nlpMatch = nlpMatcher.match(scopedTemplates, response);
                if (nlpMatch.isPresent()) {
                    return new ProtocolMatchResult.Matched(
                            nlpMatch.get().templateName(), nlpMatch.get().params(), nlpMatch.get().score());
                }
            }
        }

        return new ProtocolMatchResult.Unmatched(response);
    }

    public void deregister(String protocolName) {
        protocols.remove(protocolName);
        templateStrings.remove(protocolName);
        templateKeywordCache.remove(protocolName);
    }

    public boolean isRegistered(String protocolName) {
        return protocols.containsKey(protocolName);
    }

    private Map<String, String> scopeCandidates(Map<String, String> templates,
                                                 Map<String, Set<String>> keywordMap,
                                                 String input) {
        Map<String, String> scoped = new LinkedHashMap<>();
        for (var entry : templates.entrySet()) {
            Set<String> keywords = keywordMap.get(entry.getKey());
            if (keywords == null || keywords.isEmpty()) {
                scoped.put(entry.getKey(), entry.getValue());
                continue;
            }
            double score = TemplateKeywords.affinity(keywords, input);
            if (score >= affinityThreshold) {
                scoped.put(entry.getKey(), entry.getValue());
            }
        }
        return scoped;
    }
}
