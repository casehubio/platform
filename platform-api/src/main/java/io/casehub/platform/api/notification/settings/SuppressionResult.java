package io.casehub.platform.api.notification.settings;

/**
 * Result of suppression evaluation.
 *
 * @param isMuted          whether notification is muted (drops entirely)
 * @param isSnoozed        whether user has active snooze (suppresses external channels)
 * @param quietHoursActive whether notification falls within quiet hours (suppresses external channels)
 */
public record SuppressionResult(
        boolean isMuted,
        boolean isSnoozed,
        boolean quietHoursActive
) {}
