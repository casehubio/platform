package io.casehub.platform.api.notification.settings;

/**
 * Mute rule scope.
 */
public enum MuteScope {
    /**
     * Mute notifications for a specific entity (e.g., a work item, case).
     * Requires matching both {@code entityType} and {@code entityId}.
     */
    ENTITY,

    /**
     * Mute notifications in a category (e.g., "comments").
     * If {@code entityType} is non-null, also requires matching {@code entityType}
     * (optional refinement: "mute comments on work-items only").
     * If {@code entityType} is null, matches the category for all entity types.
     */
    CATEGORY
}
