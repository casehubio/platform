package io.casehub.platform.expression;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpExpressionEngineRegistry implements ExpressionEngineRegistry {

    private static final String MESSAGE =
            "No ExpressionEngine available — add casehub-platform-expression to the classpath";

    @Override
    public void register(ExpressionEngine engine) {}

    @Override
    public Optional<ExpressionEngine> resolve(String type) {
        return Optional.empty();
    }

    @Override
    public <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType,
            Map<String, Object> variables) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public void validate(String type, String expression) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
