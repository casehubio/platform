package io.casehub.platform.api.notification;

import java.util.Objects;

/**
 * Query parameters for listing notifications with cursor-based pagination.
 *
 * @param userId     notification recipient (required)
 * @param tenancyId  tenant isolation (required)
 * @param status     filter by status (nullable = all statuses)
 * @param category   filter by category (nullable = all categories)
 * @param cursor     pagination cursor (nullable = start from beginning)
 * @param limit      page size (must be positive)
 */
public record NotificationQuery(
        String userId,
        String tenancyId,
        NotificationStatus status,
        String category,
        String cursor,
        int limit
) {
    public NotificationQuery {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
