package io.casehub.platform.api.subscription;

import java.util.Objects;

public record SubscriptionQuery(
        String ownerId,
        String tenancyId,
        SubscriptionScope scope,
        Boolean enabled,
        String cursor,
        int limit
) {
    public SubscriptionQuery {
        Objects.requireNonNull(tenancyId, "tenancyId");
        scope = scope != null ? scope : SubscriptionScope.USER;
        if (scope == SubscriptionScope.USER) {
            Objects.requireNonNull(ownerId, "ownerId required for USER scope");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
