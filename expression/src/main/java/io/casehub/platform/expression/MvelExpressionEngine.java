package io.casehub.platform.expression;

import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionCompilationException;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.casehub.platform.api.expression.ExpressionEvaluationException;
import jakarta.enterprise.context.ApplicationScoped;
import org.mvel3.Evaluator;
import org.mvel3.MVEL;
import org.mvel3.Type;
import org.mvel3.transpiler.context.Declaration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class MvelExpressionEngine implements ExpressionEngine {

    private final ConcurrentHashMap<CacheKey, CompiledExpression<?, ?>> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static <R> Evaluator<Map<String, Object>, Void, R> compileWithTypes(
            String expression, Map<String, Object> context, Class<R> resultType) {
        Map<String, Type<?>> types = MVEL.getTypeMap(context);
        return MVEL.<Object>map(Declaration.from(types))
                   .<R>out(resultType)
                   .expression(expression)
                   .imports(Collections.emptySet())
                   .compile();
    }

    @Override
    public String type() {return "mvel";}

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
        Objects.requireNonNull(contextType, "contextType");
        Objects.requireNonNull(resultType, "resultType");

        Map<String, Object> boundVars = variables.isEmpty() ? Map.of() : Map.copyOf(variables);

        var key = new CacheKey(expression, contextType, resultType, boundVars);
        return (CompiledExpression<C, R>) cache.computeIfAbsent(key,
                                                                k -> new LazyMvelExpression<>(expression, resultType, boundVars));
    }

    @Override
    public void validate(String expression) {
        Objects.requireNonNull(expression, "expression");
        // MVEL3 is a transpiler — syntax validation requires full type context.
        // validate() is syntactic-only (no type params), so meaningful validation
        // is not possible. Callers who need full validation should call compile().
        if (expression.isBlank()) {
            throw new ExpressionCompilationException("MVEL expression must not be blank");
        }}

    private static class LazyMvelExpression<R> implements CompiledExpression<Map<String, Object>, R> {
        private final    String                                  expression;
        private final    Class<R>                                resultType;
        private final    Map<String, Object>                     boundVars;
        private volatile Evaluator<Map<String, Object>, Void, R> delegate;

        LazyMvelExpression(String expression, Class<R> resultType, Map<String, Object> boundVars) {
            this.expression = expression;
            this.resultType = resultType;
            this.boundVars  = boundVars;
        }

        @Override
        public String type() {return "mvel";}

        @Override
        public R eval(Map<String, Object> context) {
            Map<String, Object> evalContext;
            if (boundVars.isEmpty()) {
                evalContext = context;
            } else {
                evalContext = new HashMap<>(context);
                evalContext.putAll(boundVars);
            }

            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        try {
                            delegate = compileWithTypes(expression, evalContext, resultType);
                        } catch (Exception e) {
                            throw new ExpressionCompilationException(
                                    "Failed to compile MVEL expression: " + expression, e);
                        }
                    }
                }
            }
            try {
                return delegate.eval(evalContext);
            } catch (ExpressionCompilationException e) {
                throw e;
            } catch (Exception e) {
                throw new ExpressionEvaluationException(
                        "MVEL evaluation failed for: " + expression, e);
            }
        }
    }

    private record CacheKey(String expression, Class<?> contextType,
                            Class<?> resultType, Map<String, Object> variables) {}
}
