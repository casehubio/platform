package io.casehub.platform.api.notification.settings;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Quiet hours configuration — recurring daily time window where external
 * channel delivery is suppressed.
 *
 * <p>Handles midnight crossing: when {@code start >= end}, the window spans
 * midnight (e.g., 22:00–07:00).
 *
 * @param start    start time (inclusive) in user's local timezone
 * @param end      end time (exclusive) in user's local timezone
 * @param timezone user's timezone for evaluation
 */
public record QuietHours(
        LocalTime start,
        LocalTime end,
        ZoneId timezone
) {
    public QuietHours {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(timezone, "timezone");
    }
}
