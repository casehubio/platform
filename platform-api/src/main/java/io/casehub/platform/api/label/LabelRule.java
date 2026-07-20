package io.casehub.platform.api.label;

import io.casehub.platform.api.expression.CompiledExpression;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record LabelRule(
        String name,
        CompiledExpression<Map<String, Object>, Boolean> condition,
        List<LabelAction> actions,
        Set<String> triggerEvents) {

    public LabelRule {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        actions       = List.copyOf(actions);
        triggerEvents = triggerEvents == null ? Set.of() : Set.copyOf(triggerEvents);
    }

    public LabelRule(String name, CompiledExpression<Map<String, Object>, Boolean> condition,
                     List<LabelAction> actions) {
        this(name, condition, actions, Set.of());
    }

    public static List<LabelAction> evaluate(
            List<LabelRule> rules, Map<String, Object> context) {
        return rules.stream()
                    .filter(r -> Boolean.TRUE.equals(r.condition().eval(context)))
                    .flatMap(r -> r.actions().stream())
                    .toList();
    }

    public static List<LabelAction> evaluate(
            List<LabelRule> rules, Map<String, Object> context, String event) {
        return rules.stream()
                    .filter(r -> r.triggerEvents().isEmpty() || r.triggerEvents().contains(event))
                    .filter(r -> Boolean.TRUE.equals(r.condition().eval(context)))
                    .flatMap(r -> r.actions().stream())
                    .toList();
    }
}
