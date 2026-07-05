package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * Query parameters for listing subscriptions with cursor-based pagination.
 *
 * @param userId    subscription owner (required)
 * @param tenancyId tenant isolation (required)
 * @param enabled   filter by enabled state (nullable = all subscriptions)
 * @param cursor    pagination cursor (nullable = start from beginning)
 * @param limit     page size (must be positive)
 */
public record SubscriptionQuery(
        String userId,
        String tenancyId,
        Boolean enabled,
        String cursor,
        int limit
) {
    public SubscriptionQuery {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
