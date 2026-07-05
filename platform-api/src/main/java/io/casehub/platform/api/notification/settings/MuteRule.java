package io.casehub.platform.api.notification.settings;

import java.time.Instant;
import java.util.Objects;

/**
 * Mute rule — suppresses notifications matching the scope.
 *
 * <p>Two scopes:
 * <ul>
 *   <li>{@link MuteScope#ENTITY} — mutes notifications where {@code source.entityType}
 *       matches {@code entityType} AND {@code source.entityId} matches {@code scopeId}.
 *       {@code entityType} is required.</li>
 *   <li>{@link MuteScope#CATEGORY} — mutes notifications where {@code category}
 *       matches {@code scopeId}. If {@code entityType} is non-null, also requires
 *       matching {@code entityType} (optional refinement: "mute comments on work-items only").
 *       If {@code entityType} is null, matches the category for all entity types.</li>
 * </ul>
 *
 * @param id         UUIDv7 identifier
 * @param userId     user identifier
 * @param tenancyId  tenant isolation
 * @param scope      mute scope (ENTITY or CATEGORY)
 * @param scopeId    entityId (ENTITY scope) or category (CATEGORY scope)
 * @param entityType required for ENTITY scope; nullable for CATEGORY (optional refinement)
 * @param createdAt  creation timestamp
 * @param expiresAt  expiration timestamp; nullable = permanent until manually removed
 */
public record MuteRule(
        String id,
        String userId,
        String tenancyId,
        MuteScope scope,
        String scopeId,
        String entityType,
        Instant createdAt,
        Instant expiresAt
) {
    public MuteRule {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
