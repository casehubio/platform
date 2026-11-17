package io.casehub.yaml.core.condition;

import java.util.Map;

@FunctionalInterface
public interface ConditionDelegate {
    boolean evaluate(String expression, Map<String, Object> bindings);
}
