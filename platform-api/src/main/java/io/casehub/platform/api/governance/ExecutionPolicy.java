package io.casehub.platform.api.governance;

public record ExecutionPolicy(Integer timeoutMs, RetryPolicy retries, CircuitBreakerPolicy circuitBreaker) {

    public ExecutionPolicy(Integer timeoutMs, RetryPolicy retries) {
        this(timeoutMs, retries, null);
    }

    public ExecutionPolicy() {
        this(null, new RetryPolicy(), null);
    }

    public static ExecutionPolicy noRetry() {
        return new ExecutionPolicy(null, new RetryPolicy(1, 0), null);
    }
}
