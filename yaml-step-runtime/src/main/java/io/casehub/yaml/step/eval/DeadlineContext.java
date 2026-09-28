package io.casehub.yaml.step.eval;

import java.time.Duration;
import java.util.Optional;

public final class DeadlineContext {

    public static final DeadlineContext NONE = new DeadlineContext(Long.MAX_VALUE);

    private final long deadlineNanos;

    private DeadlineContext(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public DeadlineContext withTimeout(Duration timeout) {
        long candidate = System.nanoTime() + timeout.toNanos();
        return new DeadlineContext(Math.min(deadlineNanos, candidate));
    }

    public Optional<Duration> remainingTime() {
        if (deadlineNanos == Long.MAX_VALUE) return Optional.empty();
        long remaining = deadlineNanos - System.nanoTime();
        return Optional.of(remaining > 0 ? Duration.ofNanos(remaining) : Duration.ZERO);
    }

    public boolean isExpired() {
        return deadlineNanos != Long.MAX_VALUE && System.nanoTime() >= deadlineNanos;
    }

    public boolean hasDeadline() {
        return deadlineNanos != Long.MAX_VALUE;
    }
}
