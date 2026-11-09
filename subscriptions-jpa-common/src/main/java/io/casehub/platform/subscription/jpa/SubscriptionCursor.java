package io.casehub.platform.subscription.jpa;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * Keyset pagination cursor shared by the Quarkus and Spring JPA subscription stores.
 *
 * <p>The cursor carries the full {@link Instant} of the last row (ISO-8601, nanosecond
 * precision) so the {@code createdAt < :cursor OR (createdAt = :cursor AND id < :id)}
 * predicate compares against the exact database value. Truncating to milliseconds
 * skipped rows that shared a millisecond with the previous page's last row.
 *
 * <p>Legacy cursors that encoded epoch milliseconds are still accepted.
 */
public final class SubscriptionCursor {

    private static final char SEPARATOR = '|';

    private SubscriptionCursor() {}

    public static String encode(Instant createdAt, String id) {
        String raw = createdAt.toString() + SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                     .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return the decoded cursor, or {@code null} when the cursor is malformed
     */
    public static Value decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int    sep = raw.indexOf(SEPARATOR);
            if (sep <= 0) {
                return null;
            }
            String timestamp = raw.substring(0, sep);
            String id        = raw.substring(sep + 1);
            return new Value(parseTimestamp(timestamp), id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return null;
        }
    }

    private static Instant parseTimestamp(String timestamp) {
        if (timestamp.chars().allMatch(Character::isDigit)) {
            return Instant.ofEpochMilli(Long.parseLong(timestamp));
        }
        return Instant.parse(timestamp);
    }

    public record Value(Instant createdAt, String id) {}
}
