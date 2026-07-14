package io.casehub.platform.api.expression;

import java.util.Map;
import java.util.Optional;

/**
 * Dispatches to {@link ExpressionEngine} instances by type key.
 *
 * <p>CDI-discovered in the real implementation. The {@code @DefaultBean}
 * no-op implementation throws {@link UnsupportedOperationException} on
 * {@code compile()} and {@code validate()}, directing developers to add
 * the expression module to the classpath.
 */
public interface ExpressionEngineRegistry {

    void register(ExpressionEngine engine);

    Optional<ExpressionEngine> resolve(String type);

    <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType);

    <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType,
            Map<String, Object> variables);

    void validate(String type, String expression);
}
