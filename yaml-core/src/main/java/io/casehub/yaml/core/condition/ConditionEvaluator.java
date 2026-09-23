package io.casehub.yaml.core.condition;

import java.util.function.Function;

public final class ConditionEvaluator {

    private final Function<String, Boolean> expressionDelegate;

    public ConditionEvaluator(Function<String, Boolean> expressionDelegate) {
        this.expressionDelegate = expressionDelegate;
    }

    public boolean evaluate(String resolved) {
        try {
            return Truthiness.isTruthy(resolved);
        } catch (IllegalArgumentException e) {
            if (expressionDelegate == null) {
                throw new ConditionEvaluationException(
                        "Cannot evaluate '" + resolved + "' — no expression engine configured", e);
            }
            return expressionDelegate.apply(resolved);
        }
    }
}
