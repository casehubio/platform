package io.casehub.yaml.core.playbook;

import io.casehub.yaml.core.module.YamlModuleParameter;
import io.casehub.yaml.core.type.TypedMap;
import io.casehub.yaml.core.type.TypedName;
import io.casehub.yaml.core.type.TypedVariables;
import io.casehub.yaml.plugin.api.ParameterType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PlaybookVariableParser {

    public record ParsedVariables(
            Map<String, YamlModuleParameter> params,
            TypedMap vars) {
        public static final ParsedVariables EMPTY =
            new ParsedVariables(Map.of(), new TypedMap(Map.of(), Map.of()));
    }

    private PlaybookVariableParser() {}

    public static ParsedVariables parse(Map<String, Object> content) {
        Map<String, YamlModuleParameter> params = parseParams(content.get("params"));
        TypedMap vars = parseVars(content.get("vars"));
        return new ParsedVariables(params, vars);
    }

    private static TypedMap parseVars(Object raw) {
        if (raw == null) return new TypedMap(Map.of(), Map.of());
        if (!(raw instanceof Map<?, ?>))
            throw new IllegalArgumentException("vars: must be a map");
        return TypedVariables.parse(asStringKeyedMap(raw));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, YamlModuleParameter> parseParams(Object raw) {
        if (raw == null) return Map.of();
        if (!(raw instanceof Map<?, ?>))
            throw new IllegalArgumentException("params: must be a map");
        var result = new LinkedHashMap<String, YamlModuleParameter>();
        for (var entry : asStringKeyedMap(raw).entrySet()) {
            TypedName tn = TypedName.parse(entry.getKey());
            ParameterType paramType = tn.isScalar()
                ? tn.scalarType().toParameterType()
                : ParameterType.OBJECT;
            Object value = entry.getValue();

            if (value == null) {
                result.put(tn.name(), YamlModuleParameter.builder()
                    .type(paramType).required(true).build());
            } else if (value instanceof Map<?, ?> constraintMap) {
                var map = asStringKeyedMap(constraintMap);
                var builder = YamlModuleParameter.builder().type(paramType);
                if (map.containsKey("default"))
                    builder.defaultValue(String.valueOf(map.get("default")));
                else
                    builder.required(true);
                if (map.get("minimum") instanceof Number n) builder.minimum(n);
                if (map.get("maximum") instanceof Number n) builder.maximum(n);
                if (map.get("minLength") instanceof Number n) builder.minLength(n.intValue());
                if (map.get("maxLength") instanceof Number n) builder.maxLength(n.intValue());
                if (map.get("pattern") instanceof String s) builder.pattern(s);
                if (map.get("allowedValues") instanceof List<?> list)
                    builder.allowedValues(list.stream().map(String::valueOf).toList());
                if (map.get("constraintDescription") instanceof String s)
                    builder.constraintDescription(s);
                result.put(tn.name(), builder.build());
            } else {
                result.put(tn.name(), YamlModuleParameter.builder()
                    .type(paramType).defaultValue(String.valueOf(value)).build());
            }
        }
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyedMap(Object raw) {
        return (Map<String, Object>) raw;
    }
}
