package io.casehub.platform.simulation;

import java.time.Duration;

public record TimedEntry<E>(E event, Duration delay, String label, String qualifiedName) {

    public TimedEntry(E event, Duration delay) {
        this(event, delay, null, null);
    }

    public TimedEntry(E event, Duration delay, String label) {
        this(event, delay, label, null);
    }

    public TimedEntry {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (delay == null) {
            throw new IllegalArgumentException("delay must not be null");
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
    }

    public <R> TimedEntry<R> map(java.util.function.Function<E, R> mapper) {
        return new TimedEntry<>(mapper.apply(event), delay, label, qualifiedName);
    }
}
