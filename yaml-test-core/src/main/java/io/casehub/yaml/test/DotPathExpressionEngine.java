package io.casehub.yaml.test;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;

import java.util.Map;
import java.util.List;
import java.util.Optional;

public final class DotPathExpressionEngine implements ExpressionEngine, ExpressionEngineRegistry {

    private static final String TYPE = "dot-path";

    @Override
    public String type() { return TYPE; }

    @SuppressWarnings("unchecked")
    @Override
    public <C, R> CompiledExpression<C, R> compile(String expression,
                                                    Class<C> contextType, Class<R> resultType) {
        String path = expression.replaceAll("\\[(\\d+)\\]", ".$1");
        return new CompiledExpression<>() {
            @Override public String type() { return TYPE; }
            @Override public R eval(C context) {
                if (!(context instanceof Map<?, ?> map)) return null;
                Object current = map;
                for (String segment : path.split("\\.")) {
                    if (current instanceof Map<?, ?> m) {
                        current = m.get(segment);
                    } else if (current instanceof List<?> list) {
                        try {
                            int index = Integer.parseInt(segment);
                            if (index < 0 || index >= list.size()) return null;
                            current = list.get(index);
                        } catch (NumberFormatException e) {
                            return null;
                        }
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
