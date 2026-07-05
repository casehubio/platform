package io.casehub.platform.notification.jpa;

import io.casehub.platform.api.notification.AllNotificationsRead;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationCreated;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStatusChanged;
import io.casehub.platform.api.notification.ReactiveNotificationStore;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed reactive notification store using Hibernate Reactive Panache.
 * Native {@link ReactiveNotificationStore} implementation — not a bridge.
 *
 * <p>Cursor pagination uses keyset on {@code (created_at DESC, id DESC)}.
 * Cursor encodes {@code createdAt|id} as Base64.
 */
@ApplicationScoped
public class JpaReactiveNotificationStore implements ReactiveNotificationStore {

    @Inject
    Event<NotificationCreated> createdEvent;

    @Inject
    Event<NotificationStatusChanged> statusChangedEvent;

    @Inject
    Event<AllNotificationsRead> allReadEvent;

    @Override
    public Uni<Notification> store(NotificationInput input) {
        return Panache.withTransaction(() -> {
            NotificationEntity entity = NotificationEntity.fromInput(input);
            return entity.persist()
                    .replaceWith(entity);
        }).map(entity -> {
            Notification notification = entity.toNotification();
            createdEvent.fireAsync(new NotificationCreated(notification));
            return notification;
        });
    }

    @Override
    public Uni<List<Notification>> storeAll(List<NotificationInput> inputs) {
        return Panache.withTransaction(() -> {
            List<NotificationEntity> entities = new ArrayList<>(inputs.size());
            Uni<Void> chain = Uni.createFrom().voidItem();
            for (NotificationInput input : inputs) {
                NotificationEntity entity = NotificationEntity.fromInput(input);
                entities.add(entity);
                chain = chain.chain(() -> entity.persist().replaceWithVoid());
            }
            return chain.replaceWith(entities);
        }).map(entities -> {
            List<Notification> notifications = new ArrayList<>(entities.size());
            for (NotificationEntity entity : entities) {
                Notification notification = entity.toNotification();
                notifications.add(notification);
                createdEvent.fireAsync(new NotificationCreated(notification));
            }
            return notifications;
        });
    }

    @Override
    public Uni<NotificationPage> find(NotificationQuery query) {
        return Panache.withSession(() -> {
            StringBuilder hql = new StringBuilder(
                    "FROM NotificationEntity WHERE userId = ?1 AND tenancyId = ?2");
            List<Object> params = new ArrayList<>();
            params.add(query.userId());
            params.add(query.tenancyId());
            int paramIndex = 3;

            if (query.status() != null) {
                hql.append(" AND status = ?").append(paramIndex);
                params.add(query.status());
                paramIndex++;
            }
            if (query.category() != null) {
                hql.append(" AND category = ?").append(paramIndex);
                params.add(query.category());
                paramIndex++;
            }

            if (query.cursor() != null) {
                CursorValue cursor = decodeCursor(query.cursor());
                hql.append(" AND (createdAt < ?").append(paramIndex);
                params.add(cursor.createdAt);
                paramIndex++;
                hql.append(" OR (createdAt = ?").append(paramIndex);
                params.add(cursor.createdAt);
                paramIndex++;
                hql.append(" AND id < ?").append(paramIndex).append("))");
                params.add(cursor.id);
                paramIndex++;
            }

            hql.append(" ORDER BY createdAt DESC, id DESC");

            // Fetch limit + 1 to detect hasMore
            int fetchLimit = query.limit() + 1;

            return NotificationEntity.<NotificationEntity>find(hql.toString(), params.toArray())
                    .range(0, fetchLimit - 1)
                    .list()
                    .map(entities -> {
                        boolean hasMore = entities.size() > query.limit();
                        List<NotificationEntity> pageEntities = hasMore
                                ? entities.subList(0, query.limit())
                                : entities;

                        List<Notification> notifications = new ArrayList<>(pageEntities.size());
                        for (NotificationEntity entity : pageEntities) {
                            notifications.add(entity.toNotification());
                        }

                        String nextCursor = null;
                        if (hasMore && !pageEntities.isEmpty()) {
                            NotificationEntity last = pageEntities.getLast();
                            nextCursor = encodeCursor(last.createdAt, last.id);
                        }
                        return new NotificationPage(notifications, nextCursor);
                    });
        });
    }

    @Override
    public Uni<Long> unreadCount(String userId, String tenancyId) {
        return Panache.withSession(() ->
                NotificationEntity.count(
                        "userId = ?1 AND tenancyId = ?2 AND status = ?3",
                        userId, tenancyId, NotificationStatus.UNREAD));
    }

    @Override
    public Uni<Optional<Notification>> markRead(String id, String userId, String tenancyId) {
        return Panache.withTransaction(() ->
                NotificationEntity.<NotificationEntity>find(
                                "id = ?1 AND userId = ?2 AND tenancyId = ?3 AND status != ?4",
                                id, userId, tenancyId, NotificationStatus.DISMISSED)
                        .firstResult()
                        .map(entity -> {
                            if (entity == null) {
                                return Optional.<Notification>empty();
                            }
                            NotificationStatus previousStatus = entity.status;
                            entity.status = NotificationStatus.READ;
                            entity.readAt = Instant.now();
                            Notification notification = entity.toNotification();
                            statusChangedEvent.fireAsync(
                                    new NotificationStatusChanged(notification, previousStatus));
                            return Optional.of(notification);
                        }));
    }

    @Override
    public Uni<Optional<Notification>> dismiss(String id, String userId, String tenancyId) {
        return Panache.withTransaction(() ->
                NotificationEntity.<NotificationEntity>find(
                                "id = ?1 AND userId = ?2 AND tenancyId = ?3 AND status != ?4",
                                id, userId, tenancyId, NotificationStatus.DISMISSED)
                        .firstResult()
                        .map(entity -> {
                            if (entity == null) {
                                return Optional.<Notification>empty();
                            }
                            NotificationStatus previousStatus = entity.status;
                            entity.status = NotificationStatus.DISMISSED;
                            entity.dismissedAt = Instant.now();
                            Notification notification = entity.toNotification();
                            statusChangedEvent.fireAsync(
                                    new NotificationStatusChanged(notification, previousStatus));
                            return Optional.of(notification);
                        }));
    }

    @Override
    public Uni<Integer> markAllRead(String userId, String tenancyId) {
        return Panache.withTransaction(() -> {
            Instant now = Instant.now();
            return NotificationEntity.update(
                            "status = ?1, readAt = ?2 WHERE userId = ?3 AND tenancyId = ?4 AND status = ?5",
                            NotificationStatus.READ, now, userId, tenancyId, NotificationStatus.UNREAD)
                    .map(count -> {
                        if (count > 0) {
                            allReadEvent.fireAsync(new AllNotificationsRead(userId, tenancyId, count));
                        }
                        return count;
                    });
        });
    }

    // Cursor encoding: "createdAt_epochMillis|id" → Base64
    private static String encodeCursor(Instant createdAt, String id) {
        String raw = createdAt.toEpochMilli() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    private static CursorValue decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor));
            int sep = raw.indexOf('|');
            if (sep == -1) return null;
            long epochMillis = Long.parseLong(raw.substring(0, sep));
            String id = raw.substring(sep + 1);
            return new CursorValue(Instant.ofEpochMilli(epochMillis), id);
        } catch (IllegalArgumentException e) {
            // Catches both Base64 decoding errors and NumberFormatException
            return null;
        }
    }

    private record CursorValue(Instant createdAt, String id) {}
}
