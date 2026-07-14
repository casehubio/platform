package io.casehub.platform.api.expression;

import java.util.Map;

/**
 * Factory that compiles string expressions into {@link CompiledExpression}.
 * One engine per expression language.
 *
 * <p>{@link #compile(String, Class, Class)} compiles an expression for a
 * given context and result type. The overload with {@code variables} binds
 * external variables at compile time — the expression-language equivalent
 * of prepared statements.
 *
 * <p>{@link #validate(String)} is syntactic-only: it takes no type
 * parameters, so it cannot verify property existence or type compatibility.
 * An expression that passes {@code validate()} may still fail
 * {@code compile()} for a given context type. Callers who need full
 * type-aware validation should call {@code compile()} directly and discard
 * the result.
 */
public interface ExpressionEngine {

    String type();

    <C, R> CompiledExpression<C, R> compile(
            String expression, Class<C> contextType, Class<R> resultType);

    <C, R> CompiledExpression<C, R> compile(
            String expression, Class<C> contextType, Class<R> resultType,
            Map<String, Object> variables);

    void validate(String expression);

    default boolean supportsStringCreation() { return true; }
}
