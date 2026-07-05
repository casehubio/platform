package io.casehub.platform.api.notification.settings;

import java.time.Instant;
import java.util.Objects;

/**
 * Routing layer input for creating a mute rule. Store generates the id and
 * createdAt timestamp.
 *
 * @param userId     user identifier
 * @param tenancyId  tenant isolation
 * @param scope      mute scope (ENTITY or CATEGORY)
 * @param scopeId    entityId (ENTITY scope) or category (CATEGORY scope)
 * @param entityType required for ENTITY scope; nullable for CATEGORY (optional refinement)
 * @param expiresAt  expiration timestamp; nullable = permanent until manually removed
 */
public record MuteRuleInput(
        String userId,
        String tenancyId,
        MuteScope scope,
        String scopeId,
        String entityType,
        Instant expiresAt
) {
    public MuteRuleInput {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(scopeId, "scopeId");
    }
}
