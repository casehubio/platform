package io.casehub.yaml.step.eval;

import java.util.Map;

@FunctionalInterface
public interface TransformDelegate {
    Map<String, Object> transform(String expression, Map<String, Object> input);
}
