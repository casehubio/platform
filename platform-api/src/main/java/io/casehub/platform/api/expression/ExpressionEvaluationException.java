package io.casehub.platform.api.expression;

/**
 * Thrown when a compiled expression fails at evaluation time — null field
 * access, type mismatch, or missing property.
 *
 * <p>For boolean expressions, null field access evaluates to {@code false}
 * rather than throwing, consistent with rule engine semantics.
 */
public class ExpressionEvaluationException extends RuntimeException {

    public ExpressionEvaluationException(String message) {
        super(message);
    }

    public ExpressionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
