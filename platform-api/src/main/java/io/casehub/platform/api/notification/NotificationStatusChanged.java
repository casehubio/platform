package io.casehub.platform.api.notification;

import java.util.Objects;

/**
 * CDI event fired by non-no-op {@link NotificationStore} implementations after every
 * successful status transition via {@link NotificationStore#markRead(String, String, String)}
 * or {@link NotificationStore#dismiss(String, String, String)}.
 *
 * <p>Observers receive the updated {@link Notification} record (with new status and
 * timestamp) and the previous status. Use this event to push status updates to SSE streams
 * and update badge counts.
 *
 * <p>The no-op {@code @DefaultBean} implementation must NOT fire this event.
 *
 * @param notification   the updated notification (new status, new timestamp)
 * @param previousStatus the status before the transition
 */
public record NotificationStatusChanged(
        Notification notification,
        NotificationStatus previousStatus
) {
    public NotificationStatusChanged {
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(previousStatus, "previousStatus");
    }
}
