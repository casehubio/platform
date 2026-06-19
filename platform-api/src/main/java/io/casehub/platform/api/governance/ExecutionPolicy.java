package io.casehub.platform.api.governance;

public record ExecutionPolicy(Integer timeoutMs, RetryPolicy retries) {
    public ExecutionPolicy() {
        this(null, new RetryPolicy());
    }

    /** No retries, no timeout — execute exactly once. */
    public static ExecutionPolicy noRetry() {
        return new ExecutionPolicy(null, new RetryPolicy(1, 0));
    }
}
