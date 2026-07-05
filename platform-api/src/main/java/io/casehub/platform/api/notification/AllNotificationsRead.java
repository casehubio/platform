package io.casehub.platform.api.notification;

import java.util.Objects;

/**
 * CDI event fired by non-no-op {@link NotificationStore} implementations after every
 * successful {@link NotificationStore#markAllRead(String, String)} call.
 *
 * <p>Bulk operation event — fired once with the count of updated notifications rather than
 * per-notification events. SSE observers query {@link NotificationStore#unreadCount} to
 * push the current badge count (not assumed to be zero — new notifications may arrive
 * between markAllRead execution and CDI event dispatch).
 *
 * <p>The no-op {@code @DefaultBean} implementation must NOT fire this event.
 *
 * @param userId    the user whose notifications were marked as read
 * @param tenancyId tenant isolation
 * @param count     number of notifications marked as read
 */
public record AllNotificationsRead(
        String userId,
        String tenancyId,
        int count
) {
    public AllNotificationsRead {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(tenancyId, "tenancyId");
    }
}
