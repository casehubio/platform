package io.casehub.platform.api.delivery;

/**
 * Grouping strategy for digest notifications.
 *
 * <p>Determines how individual notifications are organized within a digest.
 */
public enum DigestGroupBy {
    /**
     * No grouping — all notifications appear in a single flat list.
     */
    FLAT,

    /**
     * Group by notification category.
     */
    CATEGORY,

    /**
     * Group by entity type and ID.
     */
    ENTITY
}
