package io.casehub.yaml.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.casehub.yaml.core.step.MatchPattern;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class MatchPatternDeserializer extends StdDeserializer<MatchPattern> {

    public MatchPatternDeserializer() {
        super(MatchPattern.class);
    }

    @Override
    public MatchPattern deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node == null || node.isNull()) {
            return new MatchPattern.DefaultPattern();
        }
        if (node.isObject()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            node.fields().forEachRemaining(e ->
                    fields.put(e.getKey(), nodeToValue(e.getValue())));
            return new MatchPattern.StructuralPattern(fields);
        }
        return new MatchPattern.ValuePattern(nodeToValue(node));
    }

    @Override
    public MatchPattern getNullValue(DeserializationContext ctxt) {
        return new MatchPattern.DefaultPattern();
    }

    static Object nodeToValue(JsonNode node) {
        if (node.isTextual()) return node.textValue();
        if (node.isInt()) return node.intValue();
        if (node.isLong()) return node.longValue();
        if (node.isDouble()) return node.doubleValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isNull()) return null;
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(e ->
                    map.put(e.getKey(), nodeToValue(e.getValue())));
            return map;
        }
        if (node.isArray()) {
            var list = new java.util.ArrayList<>();
            node.forEach(n -> list.add(nodeToValue(n)));
            return list;
        }
        return node.toString();
    }
}
