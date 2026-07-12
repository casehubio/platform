package io.casehub.platform.notification.inmem;

import io.casehub.platform.api.notification.AllNotificationsRead;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationCreated;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStatusChanged;
import io.casehub.platform.api.notification.NotificationStore;
import io.casehub.platform.api.util.UUIDv7;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Volatile in-memory {@link NotificationStore}.
 *
 * <p>Tier 4 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * JPA (Tier 2) and NoOp (Tier 1) when on the classpath.
 *
 * <p>Thread-safe. Data is ephemeral (lost on restart). Suitable for tests and
 * zero-config ephemeral single-node installs. Do NOT combine with notifications-jpa
 * in the same deployment scope.
 *
 * <p>Retention: bounded size eviction. When {@code casehub.notification.inmem.max-size}
 * is reached, evicts oldest notifications by {@code createdAt}.
 *
 * <h2>CDI Events</h2>
 * <p>Fires {@link NotificationCreated}, {@link NotificationStatusChanged}, and
 * {@link AllNotificationsRead} via {@code fireAsync()} after successful operations.
 * The package-private no-arg constructor (used by CDI proxy and unit tests) leaves
 * event fields null; null guards in methods prevent NPE in those paths.
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryNotificationStore implements NotificationStore {

    private final ConcurrentHashMap<String, Notification> store = new ConcurrentHashMap<>();
    private final int maxSize;
    private final Event<NotificationCreated> notificationCreatedEvent;
    private final Event<NotificationStatusChanged> notificationStatusChangedEvent;
    private final Event<AllNotificationsRead> allNotificationsReadEvent;

    @Inject
    public InMemoryNotificationStore(
            @ConfigProperty(name = "casehub.notification.inmem.max-size", defaultValue = "10000") int maxSize,
            Event<NotificationCreated> notificationCreatedEvent,
            Event<NotificationStatusChanged> notificationStatusChangedEvent,
            Event<AllNotificationsRead> allNotificationsReadEvent
    ) {
        this.maxSize = maxSize;
        this.notificationCreatedEvent = notificationCreatedEvent;
        this.notificationStatusChangedEvent = notificationStatusChangedEvent;
        this.allNotificationsReadEvent = allNotificationsReadEvent;
    }

    /** Used by CDI proxy subclass and unit tests. */
    InMemoryNotificationStore() {
        this(10000, null, null, null);
    }

    /** Used by unit tests for bounded-size eviction tests. */
    InMemoryNotificationStore(int maxSize) {
        this(maxSize, null, null, null);
    }

    @Override
    public Notification store(NotificationInput input) {
        var notification = toNotification(input);
        evictIfNeeded(1);
        store.put(notification.id(), notification);
        fireNotificationCreated(notification);
        return notification;
    }

    @Override
    public List<Notification> storeAll(List<NotificationInput> inputs) {
        var notifications = inputs.stream()
                .map(this::toNotification)
                .toList();
        evictIfNeeded(notifications.size());
        notifications.forEach(n -> store.put(n.id(), n));
        notifications.forEach(this::fireNotificationCreated);
        return notifications;
    }

    @Override
    public NotificationPage find(NotificationQuery query) {
        var comparator = Comparator.comparing(Notification::createdAt)
                .thenComparing(Notification::id)
                .reversed();

        var filtered = store.values().stream()
                .filter(n -> n.userId().equals(query.userId()))
                .filter(n -> n.tenancyId().equals(query.tenancyId()))
                .filter(n -> query.status() == null || n.status() == query.status())
                .filter(n -> query.category() == null || n.category().equals(query.category()))
                .filter(n -> matchesCursor(n, query.cursor()))
                .sorted(comparator)
                .limit(query.limit() + 1)
                .toList();

        boolean hasMore = filtered.size() > query.limit();
        var notifications = hasMore ? filtered.subList(0, query.limit()) : filtered;
        String nextCursor = hasMore ? encodeCursor(notifications.get(notifications.size() - 1)) : null;

        return new NotificationPage(notifications, nextCursor);
    }

    @Override
    public long unreadCount(String userId, String tenancyId) {
        return store.values().stream()
                .filter(n -> n.userId().equals(userId))
                .filter(n -> n.tenancyId().equals(tenancyId))
                .filter(n -> n.status() == NotificationStatus.UNREAD)
                .count();
    }

    @Override
    public Optional<Notification> markRead(String id, String userId, String tenancyId) {
        var now = Instant.now();
        var result = new Object() {
            Notification updated = null;
            NotificationStatus previousStatus = null;
        };

        store.compute(id, (key, notification) -> {
            if (notification == null
                    || !notification.userId().equals(userId)
                    || !notification.tenancyId().equals(tenancyId)
                    || notification.status() == NotificationStatus.DISMISSED) {
                return notification; // No change
            }

            result.previousStatus = notification.status();
            result.updated = new Notification(
                    notification.id(),
                    notification.userId(),
                    notification.tenancyId(),
                    notification.title(),
                    notification.body(),
                    notification.category(),
                    notification.severity(),
                    notification.actionUrl(),
                    notification.source(),
                    NotificationStatus.READ,
                    notification.createdAt(),
                    now,
                    notification.dismissedAt()
            );
            return result.updated;
        });

        if (result.updated != null) {
            fireNotificationStatusChanged(result.updated, result.previousStatus);
        }
        return Optional.ofNullable(result.updated);
    }

    @Override
    public Optional<Notification> dismiss(String id, String userId, String tenancyId) {
        var now = Instant.now();
        var result = new Object() {
            Notification updated = null;
            NotificationStatus previousStatus = null;
        };

        store.compute(id, (key, notification) -> {
            if (notification == null
                    || !notification.userId().equals(userId)
                    || !notification.tenancyId().equals(tenancyId)
                    || notification.status() == NotificationStatus.DISMISSED) {
                return notification; // No change
            }

            result.previousStatus = notification.status();
            result.updated = new Notification(
                    notification.id(),
                    notification.userId(),
                    notification.tenancyId(),
                    notification.title(),
                    notification.body(),
                    notification.category(),
                    notification.severity(),
                    notification.actionUrl(),
                    notification.source(),
                    NotificationStatus.DISMISSED,
                    notification.createdAt(),
                    notification.readAt(),
                    now
            );
            return result.updated;
        });

        if (result.updated != null) {
            fireNotificationStatusChanged(result.updated, result.previousStatus);
        }
        return Optional.ofNullable(result.updated);
    }

    @Override
    public int markAllRead(String userId, String tenancyId) {
        var now = Instant.now();
        var count = new int[]{0};

        store.forEach((id, notification) -> {
            if (notification.userId().equals(userId)
                    && notification.tenancyId().equals(tenancyId)
                    && notification.status() == NotificationStatus.UNREAD) {
                store.computeIfPresent(id, (key, current) -> {
                    if (current.status() == NotificationStatus.UNREAD) {
                        count[0]++;
                        return new Notification(
                                current.id(),
                                current.userId(),
                                current.tenancyId(),
                                current.title(),
                                current.body(),
                                current.category(),
                                current.severity(),
                                current.actionUrl(),
                                current.source(),
                                NotificationStatus.READ,
                                current.createdAt(),
                                now,
                                current.dismissedAt()
                        );
                    }
                    return current;
                });
            }
        });

        if (count[0] > 0) {
            fireAllNotificationsRead(userId, tenancyId, count[0]);
        }
        return count[0];
    }

    // Private Methods

    private Notification toNotification(NotificationInput input) {
        return new Notification(
                UUIDv7.generate(),
                input.userId(),
                input.tenancyId(),
                input.title(),
                input.body(),
                input.category(),
                input.severity(),
                input.actionUrl(),
                input.source(),
                NotificationStatus.UNREAD,
                Instant.now(),
                null,
                null
        );
    }

    private void evictIfNeeded(int incoming) {
        int toEvict = store.size() + incoming - maxSize;
        if (toEvict <= 0) return;

        var oldest = store.values().stream()
                .sorted(Comparator.comparing(Notification::createdAt).thenComparing(Notification::id))
                .limit(toEvict)
                .map(Notification::id)
                .toList();

        oldest.forEach(store::remove);
    }

    private boolean matchesCursor(Notification n, String cursor) {
        if (cursor == null) return true;

        var decoded = decodeCursor(cursor);
        if (decoded == null) return true; // Invalid cursor — include all

        long cursorTimestamp = decoded.timestampMs;
        String cursorId = decoded.id;

        long notificationTimestamp = n.createdAt().toEpochMilli();

        // Cursor pagination: (createdAt DESC, id DESC)
        // Cursor points to the LAST item on the previous page
        // Include if (createdAt, id) is STRICTLY LESS than cursor
        // Lexicographic comparison: first by timestamp, then by id
        if (notificationTimestamp < cursorTimestamp) return true;
        if (notificationTimestamp > cursorTimestamp) return false;
        // Same timestamp — compare IDs (DESC order, so we want id < cursorId)
        // Since we're doing DESC sort, smaller id comes LATER
        // So we include items with id < cursorId
        return n.id().compareTo(cursorId) < 0;
    }

    private String encodeCursor(Notification notification) {
        long timestampMs = notification.createdAt().toEpochMilli();
        String encoded = timestampMs + ":" + notification.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.getBytes());
    }

    private CursorData decodeCursor(String cursor) {
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(cursor));
            var parts = decoded.split(":", 2);
            if (parts.length != 2) return null;
            return new CursorData(Long.parseLong(parts[0]), parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    private void fireNotificationCreated(Notification notification) {
        if (notificationCreatedEvent != null) {
            notificationCreatedEvent.fireAsync(new NotificationCreated(notification));
        }
    }

    private void fireNotificationStatusChanged(Notification notification, NotificationStatus previousStatus) {
        if (notificationStatusChangedEvent != null) {
            notificationStatusChangedEvent.fireAsync(
                    new NotificationStatusChanged(notification, previousStatus));
        }
    }

    private void fireAllNotificationsRead(String userId, String tenancyId, int count) {
        if (allNotificationsReadEvent != null) {
            allNotificationsReadEvent.fireAsync(
                    new AllNotificationsRead(userId, tenancyId, count));
        }
    }

    /**
     * Clears all stored notifications. For test use only.
     */
    public void clear() {
        store.clear();
    }

    private record CursorData(long timestampMs, String id) {}
}
