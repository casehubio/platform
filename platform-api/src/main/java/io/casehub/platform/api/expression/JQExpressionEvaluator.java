package io.casehub.platform.api.expression;

import java.util.Objects;

public record JQExpressionEvaluator(String expression) implements StringExpressionEvaluator {

    public JQExpressionEvaluator {
        Objects.requireNonNull(expression, "expression");
    }

    @Override
    public String type() {return "jq";}
}
