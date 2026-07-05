package io.casehub.platform.api.notification;

/**
 * Notification status lifecycle.
 *
 * <pre>
 * UNREAD ──→ READ ──→ DISMISSED
 *   │                      ▲
 *   └──────────────────────┘
 *        (direct dismiss)
 * </pre>
 */
public enum NotificationStatus {
    UNREAD,
    READ,
    DISMISSED
}
