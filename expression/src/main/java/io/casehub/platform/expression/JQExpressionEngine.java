package io.casehub.platform.expression;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionCompilationException;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.ExpressionEvaluationException;
import jakarta.enterprise.context.ApplicationScoped;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class JQExpressionEngine implements ExpressionEngine {

    private final Scope                                                 rootScope;
    private final ConcurrentHashMap<String, JsonQuery>                  queryCache      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<CacheKey, CompiledExpression<?, ?>> expressionCache = new ConcurrentHashMap<>();

    public JQExpressionEngine() {
        rootScope = Scope.newEmptyScope();
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, rootScope);
    }

    @Override
    public String type() {return "jq";}

    @Override
    public <C, R> CompiledExpression<C, R> compile(
            String expression, Class<C> contextType, Class<R> resultType) {
        return compile(expression, contextType, resultType, Map.of());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C, R> CompiledExpression<C, R> compile(
            String expression, Class<C> contextType, Class<R> resultType,
            Map<String, Object> variables) {
        Objects.requireNonNull(expression, "expression");

        var key = new CacheKey(expression, resultType);
        return (CompiledExpression<C, R>) expressionCache.computeIfAbsent(key, k -> {
            JsonQuery query = compileQuery(expression);
            if (resultType == Boolean.class) {
                return new BooleanJQExpression(query, rootScope);
            }
            return new ListJQExpression(query, rootScope);
        });
    }

    @Override
    public void validate(String expression) {
        Objects.requireNonNull(expression, "expression");
        compileQuery(expression);
    }

    private JsonQuery compileQuery(String expression) {
        return queryCache.computeIfAbsent(expression, expr -> {
            try {
                return JsonQuery.compile(expr, Versions.JQ_1_6);
            } catch (Exception e) {
                throw new ExpressionCompilationException(
                        "Failed to compile JQ expression: " + expr, e);
            }
        });
    }

    private record BooleanJQExpression(JsonQuery query, Scope rootScope)
            implements CompiledExpression<JsonNode, Boolean> {

        @Override
        public String type() {return "jq";}

        @Override
        public Boolean eval(JsonNode context) {
            try {
                Scope          childScope = Scope.newChildScope(rootScope);
                List<JsonNode> out        = new ArrayList<>();
                query.apply(childScope, context, out::add);
                for (JsonNode node : out) {
                    if (node.isBoolean() && node.asBoolean()) {return true;}
                }
                return false;
            } catch (Exception e) {
                throw new ExpressionEvaluationException(
                        "JQ evaluation failed", e);
            }
        }
    }

    private record ListJQExpression(JsonQuery query, Scope rootScope)
            implements CompiledExpression<JsonNode, List<JsonNode>> {

        @Override
        public String type() {return "jq";}

        @Override
        public List<JsonNode> eval(JsonNode context) {
            try {
                Scope          childScope = Scope.newChildScope(rootScope);
                List<JsonNode> out        = new ArrayList<>();
                query.apply(childScope, context, out::add);
                return out;
            } catch (Exception e) {
                throw new ExpressionEvaluationException(
                        "JQ evaluation failed", e);
            }
        }
    }

    private record CacheKey(String expression, Class<?> resultType) {}
}
