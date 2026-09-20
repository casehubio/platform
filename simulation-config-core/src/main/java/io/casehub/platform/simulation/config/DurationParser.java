package io.casehub.platform.simulation.config;

import java.time.Duration;

public final class DurationParser {

    private DurationParser() {}

    public static Duration parse(String value) {
        if (value == null || value.isBlank()) return Duration.ZERO;
        String trimmed = value.trim();
        if (trimmed.endsWith("ms")) {
            return Duration.ofMillis(Long.parseLong(trimmed.substring(0, trimmed.length() - 2)));
        }
        if (trimmed.endsWith("s")) {
            return Duration.ofSeconds(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        }
        if (trimmed.endsWith("m")) {
            return Duration.ofMinutes(Long.parseLong(trimmed.substring(0, trimmed.length() - 1)));
        }
        return Duration.ofMillis(Long.parseLong(trimmed));
    }
}
