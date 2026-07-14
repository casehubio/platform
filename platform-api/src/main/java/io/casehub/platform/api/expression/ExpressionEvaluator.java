package io.casehub.platform.api.expression;

/**
 * Marker interface for uncompiled expression descriptors.
 *
 * <p>Carries a {@link #type()} discriminator for registry dispatch. Concrete
 * evaluators carry their own data: string-based evaluators (JQ, MVEL) carry
 * an expression string; {@link LambdaExpression} carries a {@code Function}.
 */
public interface ExpressionEvaluator {

    String type();
}
