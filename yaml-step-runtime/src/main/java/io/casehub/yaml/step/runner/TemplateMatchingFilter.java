package io.casehub.yaml.step.runner;

import io.casehub.platform.coordination.protocol.NlpProtocolMatcher;
import io.casehub.platform.coordination.protocol.ProtocolDefinition;
import io.casehub.platform.coordination.protocol.ProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import io.casehub.platform.coordination.protocol.ProtocolMatchResult;
import io.casehub.yaml.core.resolver.TemplateExtractor;
import io.casehub.yaml.core.resolver.VariableConstraint;

import java.util.LinkedHashMap;
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
