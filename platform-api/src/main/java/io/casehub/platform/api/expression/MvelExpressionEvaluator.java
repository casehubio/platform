package io.casehub.platform.api.expression;

import java.util.Objects;

public record MvelExpressionEvaluator(String expression) implements ExpressionEvaluator {

    public MvelExpressionEvaluator {
        Objects.requireNonNull(expression, "expression");
    }

    @Override
    public String type() { return "mvel"; }
}
