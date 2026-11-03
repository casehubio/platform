package io.casehub.yaml.step.eval;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.MapServiceRegistry;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeAwareParamResolverTest {

    private ExpressionEngineRegistry testRegistry() {
        var registry = new TestExpressionEngineRegistry();
        ExpressionEngine engine = new ExpressionEngine() {
            @Override public String type() { return "test"; }
            @Override @SuppressWarnings("unchecked")
            public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt) {
                return (CompiledExpression<C, R>) new DotPathExpression(expr);
            }
            @Override
            public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt, Map<String, Object> vars) {
                return compile(expr, ct, rt);
            }
            @Override public void validate(String expr) {}
        };
        registry.register(engine);
        registry.registerDefault(ExpressionContext.PARAMETER, "test");
        return registry;
    }

    private record DotPathExpression(String expr) implements CompiledExpression<Map<String, Object>, Object> {
        @Override public String type() { return "test"; }
        @Override public Object eval(Map<String, Object> ctx) {
            String[] parts = expr.split("\\.");
            Object current = ctx;
            for (String part : parts) {
                if (current instanceof Map<?, ?> map) {
                    current = map.get(part);
                } else {
                    return null;
                }
            }
            return current;
        }
    }

    @Test
    void stringParams_resolvedViaVariableResolver() {
        var services = new MapServiceRegistry();
        services.register(ExpressionEngineRegistry.class, testRegistry());
        var resolver = new VariableResolver(Map.of("var", name -> "hello"), Set.of());
        var paramResolver = new TypeAwareParamResolver(resolver, services);

        Map<String, Parameter> inputs = Map.of(
                "greeting", new Parameter(ParameterType.STRING, false, null, null, null, null));
        Map<String, Object> result = paramResolver.resolve(
                Map.of("greeting", "${var.name}"), inputs);
        assertThat(result).containsKey("greeting");
    }

    @Test
    void expressionParams_resolvedViaExpressionEngine() {
        var services = new MapServiceRegistry();
        services.register(ExpressionEngineRegistry.class, testRegistry());
        Map<String, Object> data = Map.of("output", Map.of("risk", "high"));
        var resolver = new VariableResolver(Map.of(), Set.of())
                .withObjectScope("results", data);
        var paramResolver = new TypeAwareParamResolver(resolver, services);

        Map<String, Parameter> inputs = Map.of(
                "_subject", new Parameter(ParameterType.EXPRESSION, true, null, null, null, null));
        Map<String, Object> result = paramResolver.resolve(
                Map.of("_subject", "results.output.risk"), inputs);
        assertThat(result.get("_subject")).isEqualTo("high");
    }

    @Test
    void expressionParams_stripsDollarBraces() {
        var services = new MapServiceRegistry();
        services.register(ExpressionEngineRegistry.class, testRegistry());
        Map<String, Object> data = Map.of("output", Map.of("risk", "high"));
        var resolver = new VariableResolver(Map.of(), Set.of())
                .withObjectScope("results", data);
        var paramResolver = new TypeAwareParamResolver(resolver, services);

        Map<String, Parameter> inputs = Map.of(
                "_subject", new Parameter(ParameterType.EXPRESSION, true, null, null, null, null));
        Map<String, Object> result = paramResolver.resolve(
                Map.of("_subject", "${results.output.risk}"), inputs);
        assertThat(result.get("_subject")).isEqualTo("high");
    }

    @Test
    void expressionParams_nullValue_returnsNull() {
        var services = new MapServiceRegistry();
        services.register(ExpressionEngineRegistry.class, testRegistry());
        var resolver = new VariableResolver(Map.of(), Set.of());
        var paramResolver = new TypeAwareParamResolver(resolver, services);

        Map<String, Parameter> inputs = Map.of(
                "_subject", new Parameter(ParameterType.EXPRESSION, true, null, null, null, null));
        Map<String, Object> rawParams = new LinkedHashMap<>();
        rawParams.put("_subject", null);
        Map<String, Object> result = paramResolver.resolve(rawParams, inputs);
        assertThat(result.get("_subject")).isNull();
    }

    @Test
    void expressionParams_nonString_passthrough() {
        var services = new MapServiceRegistry();
        services.register(ExpressionEngineRegistry.class, testRegistry());
        var resolver = new VariableResolver(Map.of(), Set.of());
        var paramResolver = new TypeAwareParamResolver(resolver, services);

        Map<String, Parameter> inputs = Map.of(
                "_subject", new Parameter(ParameterType.EXPRESSION, true, null, null, null, null));
        Map<String, Object> result = paramResolver.resolve(
                Map.of("_subject", 42), inputs);
        assertThat(result.get("_subject")).isEqualTo(42);
    }

    @Test
    void missingExpressionEngineRegistry_throwsIllegalState() {
        var services = new MapServiceRegistry();
        var resolver = new VariableResolver(Map.of(), Set.of());
        var paramResolver = new TypeAwareParamResolver(resolver, services);

        Map<String, Parameter> inputs = Map.of(
                "_subject", new Parameter(ParameterType.EXPRESSION, true, null, null, null, null));
        assertThatThrownBy(() -> paramResolver.resolve(Map.of("_subject", "expr"), inputs))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void undeclaredParams_defaultToStringResolution() {
        var services = new MapServiceRegistry();
        services.register(ExpressionEngineRegistry.class, testRegistry());
        var resolver = new VariableResolver(Map.of("var", name -> "world"), Set.of());
        var paramResolver = new TypeAwareParamResolver(resolver, services);

        Map<String, Parameter> inputs = Map.of();
        Map<String, Object> result = paramResolver.resolve(
                Map.of("msg", "${var.greeting}"), inputs);
        assertThat(result).containsKey("msg");
    }

    private static class TestExpressionEngineRegistry implements ExpressionEngineRegistry {
        private final ConcurrentHashMap<String, ExpressionEngine> engines = new ConcurrentHashMap<>();
        private final EnumMap<ExpressionContext, String> defaults = new EnumMap<>(ExpressionContext.class);

        @Override public void register(ExpressionEngine engine) { engines.put(engine.type(), engine); }
        @Override public Optional<ExpressionEngine> resolve(String type) { return Optional.ofNullable(engines.get(type)); }
        @Override public <C, R> CompiledExpression<C, R> compile(String type, String expr, Class<C> ct, Class<R> rt) {
            return engines.get(type).compile(expr, ct, rt);
        }
        @Override public <C, R> CompiledExpression<C, R> compile(String type, String expr, Class<C> ct, Class<R> rt, Map<String, Object> vars) {
            return engines.get(type).compile(expr, ct, rt, vars);
        }
        @Override public void validate(String type, String expr) { engines.get(type).validate(expr); }
        @Override public void registerDefault(ExpressionContext ctx, String engineType) { defaults.put(ctx, engineType); }
        @Override public String resolveDefault(ExpressionContext ctx) { return defaults.get(ctx); }
    }
}
