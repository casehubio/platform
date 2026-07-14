package io.casehub.platform.api.expression;

/**
 * Compiled, type-safe expression ready for evaluation.
 *
 * <p>All expression languages compile down to this contract. MVEL3 wraps
 * {@code Evaluator<C, Void, R>}, JQ wraps jackson-jq's {@code JsonQuery},
 * and {@link LambdaExpression} wraps {@code Function<C, R>}.
 *
 * @param <C> context type the expression evaluates against
 * @param <R> result type the expression produces
 */
public interface CompiledExpression<C, R> {

    String type();

    R eval(C context);
}
