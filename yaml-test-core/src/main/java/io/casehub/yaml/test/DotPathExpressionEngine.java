package io.casehub.yaml.test;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;

import java.util.Map;
import java.util.Optional;

public final class DotPathExpressionEngine implements ExpressionEngine, ExpressionEngineRegistry {

    private static final String TYPE = "dot-path";

    @Override
    public String type() { return TYPE; }

    @SuppressWarnings("unchecked")
    @Override
    public <C, R> CompiledExpression<C, R> compile(String expression,
                                                    Class<C> contextType, Class<R> resultType) {
        return new CompiledExpression<>() {
            @Override public String type() { return TYPE; }
            @Override public R eval(C context) {
                if (!(context instanceof Map<?, ?> map)) return null;
                Object current = map;
                for (String segment : expression.split("\\.")) {
                    if (current instanceof Map<?, ?> m) {
                        current = m.get(segment);
                    } else {
                        return null;
                    }
                }
                return (R) current;
            }
        };
    }

    @Override
    public <C, R> CompiledExpression<C, R> compile(String expression,
                                                    Class<C> contextType, Class<R> resultType,
                                                    Map<String, Object> variables) {
        return compile(expression, contextType, resultType);
    }

    @Override
    public void validate(String expression) {}

    @Override
    public void register(ExpressionEngine engine) {}

    @Override
    public Optional<ExpressionEngine> resolve(String type) {
        return TYPE.equals(type) ? Optional.of(this) : Optional.empty();
    }

    @Override
    public <C, R> CompiledExpression<C, R> compile(String type, String expression,
                                                    Class<C> contextType, Class<R> resultType) {
        return compile(expression, contextType, resultType);
    }

    @Override
    public <C, R> CompiledExpression<C, R> compile(String type, String expression,
                                                    Class<C> contextType, Class<R> resultType,
                                                    Map<String, Object> variables) {
        return compile(expression, contextType, resultType);
    }

    @Override
    public void validate(String type, String expression) {}

    @Override
    public void registerDefault(ExpressionContext context, String engineType) {}

    @Override
    public String resolveDefault(ExpressionContext context) { return TYPE; }
}
