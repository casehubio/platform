package io.casehub.platform.api.datasource;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * Compiled filter expression for alpha network subscription filtering.
 *
 * <p>Wraps a {@link Predicate} along with its original expression text and evaluator type
 * (e.g., {@code "jq"}, {@code "mvel"}). The predicate is runtime-evaluated — expression
 * compilation must happen before constructing this record.
 *
 * <p>Implements {@link Predicate} directly, delegating to the wrapped predicate.
 *
 * @param type       evaluator type (e.g., {@code "jq"}, {@code "mvel"})
 * @param expression original filter expression text
 * @param predicate  compiled predicate — {@code test(T)} delegates here
 * @param <T>        type this filter operates on
 */
public record FilterExpression<T>(
        String type,
        String expression,
        Predicate<T> predicate
) implements Predicate<T> {

    public FilterExpression {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(predicate, "predicate");
    }

    @Override
    public boolean test(T t) {
        return predicate.test(t);
    }
}
