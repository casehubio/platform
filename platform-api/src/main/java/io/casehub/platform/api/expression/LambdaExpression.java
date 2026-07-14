package io.casehub.platform.api.expression;

import java.util.Objects;
import java.util.function.Function;

/**
 * Pre-compiled expression wrapping a {@code Function<C, R>}.
 *
 * <p>Implements both {@link ExpressionEvaluator} and {@link CompiledExpression}
 * — it is already compiled by definition. Not serialisable. Not tied to any
 * specific context type.
 *
 * <p>Intentionally outside the registry flow — no {@code LambdaExpressionEngine}
 * exists because lambdas are pre-compiled. {@code resolve("lambda")} returns
 * empty; instances are created directly, never via {@code compile()}.
 *
 * @param <C> context type
 * @param <R> result type
 */
public class LambdaExpression<C, R> implements ExpressionEvaluator, CompiledExpression<C, R> {

    private final Function<C, R> function;

    public LambdaExpression(Function<C, R> function) {
        Objects.requireNonNull(function, "function");
        this.function = function;
    }

    @Override
    public String type() { return "lambda"; }

    @Override
    public R eval(C context) { return function.apply(context); }
}
