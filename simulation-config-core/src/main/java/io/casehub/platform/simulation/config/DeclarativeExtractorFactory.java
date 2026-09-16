package io.casehub.platform.simulation.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.platform.simulation.KeyExtractor;
import io.casehub.platform.simulation.SimulationConfigException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class DeclarativeExtractorFactory {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {};
    private final ObjectMapper objectMapper;

    public DeclarativeExtractorFactory() {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    public KeyExtractor<Object> create(String spec) {
        if ("identity".equals(spec)) {
            return input -> String.valueOf(input);
        }
        if (spec.startsWith("field:")) {
            String fieldName = spec.substring("field:".length());
            return input -> String.valueOf(toMap(input).get(fieldName));
        }
        if (spec.startsWith("composite:")) {
            String[] fields = spec.substring("composite:".length()).split(",");
            return input -> {
                Map<String, Object> map = toMap(input);
                return Arrays.stream(fields)
                        .map(f -> f + "=" + map.getOrDefault(f, "null"))
                        .collect(Collectors.joining(":"));
            };
        }
        if ("rest-client".equals(spec)) {
            return input -> {
                if (input instanceof io.casehub.platform.simulation.RestInvocation ri) {
                    String path = ri.pathTemplate();
                    for (var e : ri.params().entrySet()) {
                        path = path.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
                    }
                    return ri.httpMethod() + " " + path;
                }
                return String.valueOf(input);
            };
        }
        throw new SimulationConfigException(
                "Unknown key-extractor spec: " + spec
                        + ". Valid: identity, field:<name>, composite:<f1>,<f2>, rest-client");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object input) {
        if (input instanceof Map) {
            return (Map<String, Object>) input;
        }
        return objectMapper.convertValue(input, MAP_TYPE);
    }
}
