package io.casehub.platform.api.expression;

/**
 * Thrown when an expression fails to compile — invalid syntax, transpilation
 * error, or type mismatch at compile time.
 */
public class ExpressionCompilationException extends RuntimeException {

    public ExpressionCompilationException(String message) {
        super(message);
    }

    public ExpressionCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
