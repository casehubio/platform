package io.casehub.platform.api.governance;

public record RetryPolicy(Integer maxAttempts, Integer delayMs, BackoffStrategy backoffStrategy, Integer maxDelayMs) {

    public RetryPolicy {
        if (maxAttempts != null && maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, got " + maxAttempts);
        }
        if (delayMs != null && delayMs < 0) {
            throw new IllegalArgumentException("delayMs must be >= 0, got " + delayMs);
        }
        if (maxDelayMs != null && maxDelayMs < 0) {
            throw new IllegalArgumentException("maxDelayMs must be >= 0, got " + maxDelayMs);
        }
    }

    public RetryPolicy() {
        this(3, 10000, BackoffStrategy.FIXED, null);
    }

    public RetryPolicy(Integer maxAttempts, Integer delayMs) {
        this(maxAttempts, delayMs, BackoffStrategy.FIXED, null);
    }

    public RetryPolicy(Integer maxAttempts, Integer delayMs, BackoffStrategy backoffStrategy) {
        this(maxAttempts, delayMs, backoffStrategy, null);
    }
}
