package io.casehub.platform.api.notification;

import java.util.Objects;

/**
 * CDI event fired by non-no-op {@link NotificationStore} implementations after every
 * successful {@link NotificationStore#store(NotificationInput)} call and for each
 * notification in {@link NotificationStore#storeAll(java.util.List)}.
 *
 * <p>Consumers use this event for real-time push (SSE) and other notification delivery
 * mechanisms. The no-op {@code @DefaultBean} implementation must NOT fire this event —
 * firing it would trigger push for phantom notifications that are never actually stored.
 */
public record NotificationCreated(Notification notification) {

    public NotificationCreated {
        Objects.requireNonNull(notification, "notification");
    }
}
