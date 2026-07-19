package io.casehub.platform.api.label;

import io.casehub.platform.api.expression.CompiledExpression;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LabelRule(
        String name,
        CompiledExpression<Map<String, Object>, Boolean> condition,
        List<LabelAction> actions) {

    public LabelRule {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        actions = List.copyOf(actions);
    }

    public static List<LabelAction> evaluate(
            List<LabelRule> rules, Map<String, Object> context) {
        return rules.stream()
                .filter(r -> Boolean.TRUE.equals(r.condition().eval(context)))
                .flatMap(r -> r.actions().stream())
                .toList();
    }
}
