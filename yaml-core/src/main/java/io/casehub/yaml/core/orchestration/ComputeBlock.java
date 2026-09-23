package io.casehub.yaml.core.orchestration;

import java.util.Map;
import java.util.Objects;

public record ComputeBlock(String engine, String expression) {

    public ComputeBlock {
        Objects.requireNonNull(engine, "engine must not be null");
        Objects.requireNonNull(expression, "expression must not be null");
    }

    @SuppressWarnings("unchecked")
    public static ComputeBlock parse(Map<String, Object> map, String defaultEngine) {
        String engine = (String) map.getOrDefault("engine", defaultEngine);
        String expression = (String) map.get("expression");
        if (engine == null) {
            throw new IllegalArgumentException(
                    "ComputeBlock requires an engine — specify 'engine:' or provide a default");
        }
        return new ComputeBlock(engine, expression);
    }
}
