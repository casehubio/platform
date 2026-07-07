package io.casehub.platform.api.notification;

/**
 * Notification severity level. Determines visual priority and notification channel routing.
 *
 * <p>Ordinal order encodes priority: INFO < WARNING < URGENT.
 */
public enum NotificationSeverity {
    INFO,
    WARNING,
    URGENT;

    /**
     * Returns true if this severity is at least as severe as the threshold.
     *
     * @param threshold the minimum required severity
     * @return true if this >= threshold in ordinal order
     */
    public boolean isAtLeast(NotificationSeverity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
