package io.casehub.platform.api.notification;

import java.util.List;
import java.util.Objects;

/**
 * Page of notifications with optional next-page cursor.
 *
 * @param notifications notifications in this page (defensive copy made in constructor)
 * @param nextCursor    opaque cursor for the next page (null = no more pages)
 */
public record NotificationPage(
        List<Notification> notifications,
        String nextCursor
) {
    public NotificationPage {
        Objects.requireNonNull(notifications, "notifications");
        notifications = List.copyOf(notifications);
    }
}
