package io.casehub.yaml.step.runner;

import io.casehub.platform.coordination.protocol.NlpProtocolMatcher;
import io.casehub.platform.coordination.protocol.ProtocolDefinition;
import io.casehub.platform.coordination.protocol.ProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import io.casehub.platform.coordination.protocol.ProtocolMatchResult;
import io.casehub.yaml.core.resolver.TemplateExtractor;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TemplateMatchingFilter {

    private static final String INSTANCE_KEY = "playbook";

    private final ProtocolEvaluator evaluator;
    private final Map<String, Map<String, VariableConstraint>> constraints;
    private final Map<String, TemplateExtractor> extractors;
    private final String protocolName;

    public TemplateMatchingFilter(ProtocolDefinition protocol,
                                  Map<String, Map<String, VariableConstraint>> constraints,
                                  NlpProtocolMatcher nlpMatcher) {
        Objects.requireNonNull(protocol, "protocol");
        this.protocolName = protocol.name();
        this.constraints = Map.copyOf(constraints);
        this.evaluator = new ProtocolEvaluator(nlpMatcher);
        this.evaluator.register(protocol);

        var extractorMap = new LinkedHashMap<String, TemplateExtractor>();
        protocol.templates().forEach((name, pattern) ->
                extractorMap.put(name, TemplateExtractor.compile(pattern)));
        this.extractors = Map.copyOf(extractorMap);
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
                var constraints = new LinkedHashMap<String, VariableConstraint>();
                for (var varEntry : varsMap.entrySet()) {
                    var varDef = (Map<String, Object>) varEntry.getValue();
                    constraints.put(varEntry.getKey(), parseConstraint(varDef));
                }
                constraintsMap.put(templateName, Map.copyOf(constraints));
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
                        return Optional.empty();
                    }
                    return Optional.of(new TemplateMatchEvent(
                            matched.templateName(), validated.get(), 1.0, input));
                }
            }
            return Optional.of(new TemplateMatchEvent(
                    matched.templateName(), matched.params(), 1.0, input));
        }

        return Optional.empty();
    }
}
