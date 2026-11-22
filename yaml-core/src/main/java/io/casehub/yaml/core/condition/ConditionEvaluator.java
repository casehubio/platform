package io.casehub.yaml.core.condition;

// Deliberately separate from TypeAwareParamResolver — Truthiness fast-path and boolean-only semantics differ from general parameter resolution
public final class ConditionEvaluator {

    private final ConditionDelegate expressionDelegate;

    public ConditionEvaluator(ConditionDelegate expressionDelegate) {
        this.expressionDelegate = expressionDelegate;
    }

    public boolean evaluate(String condition, io.casehub.yaml.core.resolver.VariableResolver resolver, String context) {
        if ("true".equals(condition)) {return true;}
        if ("false".equals(condition)) {return false;}

        if (io.casehub.yaml.core.resolver.VariableResolver.isSoleReference(condition)) {
            Object value = resolver.resolve(condition);
            if (value instanceof Boolean b) {return b;}
            if (value instanceof String s) {
                Boolean result = yamlTruthyOrNull(s);
                if (result != null) {return result;}
            }
        }

        String resolved = resolver.resolveString(condition, context);

        Boolean result = yamlTruthyOrNull(resolved);
        if (result != null) {return result;}

        if (expressionDelegate == null) {
            throw new ConditionEvaluationException(
                    "Cannot evaluate '" + condition + "' (resolved: '" + resolved + "') — no expression engine configured", null);
        }
        return expressionDelegate.evaluate(resolved, resolver.buildExpressionContext());
    }

    private static Boolean yamlTruthyOrNull(String value) {
        try { return Truthiness.isYamlTruthy(value); }
        catch (IllegalArgumentException e) { return null; }
    }
}
