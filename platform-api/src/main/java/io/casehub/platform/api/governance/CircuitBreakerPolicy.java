package io.casehub.platform.api.governance;

public record CircuitBreakerPolicy(int failureThreshold, int recoveryWindowMs) {

    public CircuitBreakerPolicy() {
        this(5, 30000);
    }

    public CircuitBreakerPolicy {
        if (failureThreshold < 1)
            throw new IllegalArgumentException("failureThreshold must be >= 1, got " + failureThreshold);
        if (recoveryWindowMs < 0)
            throw new IllegalArgumentException("recoveryWindowMs must be >= 0, got " + recoveryWindowMs);
    }
}
