package io.casehub.platform.api.notification.settings;

/**
 * Action to take when a notification is triggered during quiet hours.
 */
public enum QuietHoursAction {
    /**
     * Suppress the notification entirely — do not deliver it at all.
     */
    SUPPRESS,

    /**
     * Buffer the notification for delivery in the next scheduled digest.
     */
    BUFFER_FOR_DIGEST
}
