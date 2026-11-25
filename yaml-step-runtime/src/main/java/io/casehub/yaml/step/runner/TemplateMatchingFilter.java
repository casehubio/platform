package io.casehub.yaml.step.runner;

import io.casehub.platform.coordination.protocol.NlpProtocolMatcher;
import io.casehub.platform.coordination.protocol.ProtocolDefinition;
import io.casehub.platform.coordination.protocol.ProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import io.casehub.platform.coordination.protocol.ProtocolMatchResult;
import io.casehub.platform.coordination.protocol.TemplateKeywords;
import io.casehub.yaml.core.resolver.TemplateExtractor;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class TemplateMatchingFilter {

    private static final String INSTANCE_KEY = "playbook";
    private static final double NEAR_MISS_THRESHOLD = 0.2;
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private final ProtocolEvaluator evaluator;
    private final Map<String, Map<String, VariableConstraint>> constraints;
    private final Map<String, TemplateExtractor> extractors;
    private final Map<String, String> templatePatterns;
    private final Map<String, Set<String>> templateKeywords;
    private final String protocolName;
    private final boolean rephraseEnabled;

    public TemplateMatchingFilter(ProtocolDefinition protocol,
                                  Map<String, Map<String, VariableConstraint>> constraints,
                                  NlpProtocolMatcher nlpMatcher) {
        this(protocol, constraints, nlpMatcher, true);
    }

    public TemplateMatchingFilter(ProtocolDefinition protocol,
                                  Map<String, Map<String, VariableConstraint>> constraints,
                                  NlpProtocolMatcher nlpMatcher,
                                  boolean rephraseEnabled) {
        Objects.requireNonNull(protocol, "protocol");
        this.protocolName = protocol.name();
        this.constraints = Map.copyOf(constraints);
        this.rephraseEnabled = rephraseEnabled;
        this.evaluator = new ProtocolEvaluator(nlpMatcher);
        this.evaluator.register(protocol);
        this.templatePatterns = Map.copyOf(protocol.templates());

        var extractorMap = new LinkedHashMap<String, TemplateExtractor>();
        protocol.templates().forEach((name, pattern) ->
                extractorMap.put(name, TemplateExtractor.compile(pattern)));
        this.extractors = Map.copyOf(extractorMap);

        var keywordMap = new LinkedHashMap<String, Set<String>>();
        protocol.templates().forEach((name, pattern) ->
                keywordMap.put(name, TemplateKeywords.extract(pattern)));
        this.templateKeywords = Map.copyOf(keywordMap);
    }

    @SuppressWarnings("unchecked")
    public static TemplateMatchingFilter fromYaml(String name,
                                                   Map<String, Object> templatesMap,
                                                   NlpProtocolMatcher nlpMatcher) {
        var patternMap = new LinkedHashMap<String, String>();
        var constraintsMap = new LinkedHashMap<String, Map<String, VariableConstraint>>();

        for (var entry : templatesMap.entrySet()) {
            String templateName = entry.getKey();
            var templateDef = (Map<String, Object>) entry.getValue();
            String pattern = (String) templateDef.get("pattern");
            patternMap.put(templateName, pattern);

            if (templateDef.containsKey("variables")) {
                var varsMap = (Map<String, Object>) templateDef.get("variables");
                var constraintEntries = new LinkedHashMap<String, VariableConstraint>();
                for (var varEntry : varsMap.entrySet()) {
                    var varDef = (Map<String, Object>) varEntry.getValue();
                    constraintEntries.put(varEntry.getKey(), parseConstraint(varDef));
                }
                constraintsMap.put(templateName, Map.copyOf(constraintEntries));
            }
        }

        var protocol = new ProtocolDefinition(name, patternMap);
        return new TemplateMatchingFilter(protocol, constraintsMap, nlpMatcher);
    }

    @SuppressWarnings("unchecked")
    private static VariableConstraint parseConstraint(Map<String, Object> varDef) {
        String type = (String) varDef.getOrDefault("type", "string");
        boolean required = (boolean) varDef.getOrDefault("required", true);
        VariableConstraint constraint;
        if ("enum".equals(type)) {
            var values = (List<String>) varDef.get("values");
            constraint = VariableConstraint.enumOf(values.toArray(new String[0]));
        } else {
            constraint = VariableConstraint.string();
        }
        return required ? constraint : constraint.optional();
    }

    public Optional<TemplateMatchEvent> match(String input) {
        var result = evaluate(input);
        if (result instanceof TemplateMatchResult.Matched matched) {
            return Optional.of(matched.event());
        }
        return Optional.empty();
    }

    public TemplateMatchResult evaluate(String input) {
        Objects.requireNonNull(input, "input");
        var key = new ProtocolInstanceKey(protocolName, INSTANCE_KEY);
        ProtocolMatchResult result = evaluator.evaluate(key, input);

        if (result instanceof ProtocolMatchResult.Matched matched) {
            var templateConstraints = constraints.get(matched.templateName());
            if (templateConstraints != null && !templateConstraints.isEmpty()) {
                var extractor = extractors.get(matched.templateName());
                if (extractor != null) {
                    var validated = extractor.extract(input, templateConstraints);
                    if (validated.isEmpty()) {
                        return detectNearMiss(input);
                    }
                    return new TemplateMatchResult.Matched(new TemplateMatchEvent(
                            matched.templateName(), validated.get(), matched.score(), input));
                }
            }
            return new TemplateMatchResult.Matched(new TemplateMatchEvent(
                    matched.templateName(), matched.params(), matched.score(), input));
        }

        return detectNearMiss(input);
    }

    private TemplateMatchResult detectNearMiss(String input) {
        if (!rephraseEnabled) {
            return new TemplateMatchResult.PassThrough(input);
        }

        String bestTemplate = null;
        double bestAffinity = 0.0;

        for (var entry : templateKeywords.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            double affinity = TemplateKeywords.affinity(entry.getValue(), input);
            if (affinity > bestAffinity) {
                bestAffinity = affinity;
                bestTemplate = entry.getKey();
            }
        }

        if (bestTemplate != null && bestAffinity >= NEAR_MISS_THRESHOLD) {
            String pattern = templatePatterns.get(bestTemplate);
            String format = formatSuggestion(pattern);
            return new TemplateMatchResult.NearMiss(input, bestTemplate, format);
        }

        return new TemplateMatchResult.PassThrough(input);
    }

    static String formatSuggestion(String pattern) {
        String stripped = pattern.replace("[", "").replace("]", "");
        return VAR_PATTERN.matcher(stripped).replaceAll(mr -> {
            String varName = mr.group(1);
            return "[" + varName + "]";
        });
    }
}
