package io.casehub.platform.api.subscription;

import java.util.Set;

/**
 * Resolves users watching a specific entity. Application-tier implementations
 * provide the actual watch/follow tracking.
 */
public interface EntityWatcherProvider {
    /**
     * Returns user IDs of all users watching the specified entity.
     *
     * @param entityType entity type (e.g., "case", "work-item")
     * @param entityId   entity identifier
     * @param tenancyId  tenancy identifier
     * @return set of user IDs (may be empty)
     */
    Set<String> watchersOf(String entityType, String entityId, String tenancyId);
}
