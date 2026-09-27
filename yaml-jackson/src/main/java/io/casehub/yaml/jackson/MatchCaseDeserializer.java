package io.casehub.yaml.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.casehub.yaml.core.step.MatchCase;
import io.casehub.yaml.core.step.MatchPattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MatchCaseDeserializer extends StdDeserializer<MatchCase> {

    public MatchCaseDeserializer() {
        super(MatchCase.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MatchCase deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.has("default")) {
            List<Map<String, Object>> steps = parseStepList(node.get("default"));
            return new MatchCase(new MatchPattern.DefaultPattern(), null, steps);
        }

        if (node.has("pattern") && node.has("default")) {
            throw new IOException("pattern and default are mutually exclusive in a case entry");
        }

        MatchPattern pattern;
        JsonNode     patternNode = node.get("pattern");
        if (patternNode == null || patternNode.isNull()) {
            throw new IOException("case entry must contain either 'pattern' or 'default'");
        } else if (patternNode.isArray()) {
            var values = new ArrayList<>();
            patternNode.forEach(n -> values.add(MatchPatternDeserializer.nodeToValue(n)));
            pattern = new MatchPattern.AnyOfPattern(values);
        } else if (patternNode.isObject()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            patternNode.fields().forEachRemaining(e ->
                                                          fields.put(e.getKey(), MatchPatternDeserializer.nodeToValue(e.getValue())));
            pattern = new MatchPattern.StructuralPattern(fields);
        } else if (patternNode.isTextual() && "any".equals(patternNode.textValue())) {
            pattern = new MatchPattern.DefaultPattern();
        } else {
            pattern = new MatchPattern.ValuePattern(MatchPatternDeserializer.nodeToValue(patternNode));
        }

        String                    guard = node.has("guard") ? node.get("guard").asText() : null;
        List<Map<String, Object>> steps = node.has("steps") ? parseStepList(node.get("steps")) : List.of();

        return new MatchCase(pattern, guard, steps);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseStepList(JsonNode stepsNode) {
        if (stepsNode == null || !stepsNode.isArray()) return List.of();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (JsonNode stepNode : stepsNode) {
            Map<String, Object> step = new LinkedHashMap<>();
            stepNode.fields().forEachRemaining(e ->
                    step.put(e.getKey(), MatchPatternDeserializer.nodeToValue(e.getValue())));
            steps.add(step);
        }
        return steps;
    }
}
