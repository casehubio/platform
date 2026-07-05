package io.casehub.platform.api.notification.settings;

import java.time.Instant;
import java.util.Objects;

/**
 * Snooze state — suppresses external channel delivery until a time.
 *
 * <p>At most one active snooze per user. Primary key is {@code (userId, tenancyId)}.
 * Activating replaces existing.
 *
 * @param userId    user identifier
 * @param tenancyId tenant isolation
 * @param until     suppression end time
 * @param createdAt creation timestamp
 */
public record Snooze(
        String userId,
        String tenancyId,
        Instant until,
        Instant createdAt
) {
    public Snooze {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
