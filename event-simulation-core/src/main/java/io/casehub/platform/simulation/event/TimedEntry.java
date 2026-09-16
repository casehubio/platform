package io.casehub.platform.simulation.event;

import java.time.Duration;

public record TimedEntry<E>(E event, Duration delay) {

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
}
