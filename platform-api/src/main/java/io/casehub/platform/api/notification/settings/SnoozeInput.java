package io.casehub.platform.api.notification.settings;

import java.time.Instant;
import java.util.Objects;

/**
 * Routing layer input for activating snooze. Store generates createdAt timestamp.
 *
 * @param userId    user identifier
 * @param tenancyId tenant isolation
 * @param until     suppression end time
 */
public record SnoozeInput(
        String userId,
        String tenancyId,
        Instant until
) {
    public SnoozeInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(until, "until");
    }
}
