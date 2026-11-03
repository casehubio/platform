package io.casehub.yaml.step.eval;

import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.ServiceRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TypeAwareParamResolver {

    private final VariableResolver resolver;
    private final ServiceRegistry services;

    public TypeAwareParamResolver(VariableResolver resolver, ServiceRegistry services) {
        this.resolver = Objects.requireNonNull(resolver);
        this.services = Objects.requireNonNull(services);
    }

    public Map<String, Object> resolve(
            Map<String, Object> rawParams,
            Map<String, Parameter> declaredInputs) {

        Map<String, Object> resolved = new LinkedHashMap<>();
        Map<String, Object> stringParams = new LinkedHashMap<>();

        for (var entry : rawParams.entrySet()) {
            Parameter declared = declaredInputs.get(entry.getKey());
            if (declared != null && declared.type() == ParameterType.EXPRESSION) {
                resolved.put(entry.getKey(), resolveExpression(entry.getValue()));
            } else {
                stringParams.put(entry.getKey(), entry.getValue());
            }
        }

        Map<String, Object> stringResolved = resolver.resolveMap(stringParams, "step");
        resolved.putAll(stringResolved);

        return resolved;
    }

    private Object resolveExpression(Object rawValue) {
        if (rawValue == null) return null;
        if (!(rawValue instanceof String expr)) return rawValue;

        if (expr.startsWith("${") && expr.endsWith("}")
                && expr.indexOf('}') == expr.length() - 1) {
            expr = expr.substring(2, expr.length() - 1);
        }

        ExpressionEngineRegistry engines = lookupEngines();
        String engineType = engines.resolveDefault(ExpressionContext.PARAMETER);
        if (engineType == null) {
            throw new IllegalStateException(
                "No default expression engine registered for ExpressionContext.PARAMETER");
        }

        Map<String, Object> context = resolver.buildExpressionContext();
        var compiled = engines.compile(engineType, expr, Map.class, Object.class);
        return compiled.eval(context);
    }

    private ExpressionEngineRegistry lookupEngines() {
        try {
            return services.lookup(ExpressionEngineRegistry.class);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "EXPRESSION parameters require ExpressionEngineRegistry in ServiceRegistry", e);
        }
    }
}
