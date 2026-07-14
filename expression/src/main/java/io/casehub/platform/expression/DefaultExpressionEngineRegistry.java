package io.casehub.platform.expression;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class DefaultExpressionEngineRegistry implements ExpressionEngineRegistry {

    @Inject
    Instance<ExpressionEngine> engines;

    private final ConcurrentHashMap<String, ExpressionEngine> engineMap = new ConcurrentHashMap<>();

    public DefaultExpressionEngineRegistry() {}

    @PostConstruct
    void init() {
        for (ExpressionEngine engine : engines) {
            engineMap.put(engine.type(), engine);
        }
    }

    @Override
    public void register(ExpressionEngine engine) {
        engineMap.put(engine.type(), engine);
    }

    @Override
    public Optional<ExpressionEngine> resolve(String type) {
        return Optional.ofNullable(engineMap.get(type));
    }

    @Override
    public <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType) {
        return resolveEngine(type).compile(expression, contextType, resultType);
    }

    @Override
    public <C, R> CompiledExpression<C, R> compile(
            String type, String expression,
            Class<C> contextType, Class<R> resultType,
            Map<String, Object> variables) {
        return resolveEngine(type).compile(expression, contextType, resultType, variables);
    }

    @Override
    public void validate(String type, String expression) {
        resolveEngine(type).validate(expression);
    }

    private ExpressionEngine resolveEngine(String type) {
        ExpressionEngine engine = engineMap.get(type);
        if (engine == null) {
            throw new IllegalArgumentException(
                    "No ExpressionEngine registered for type '" + type + "'");
        }
        return engine;
    }
}
