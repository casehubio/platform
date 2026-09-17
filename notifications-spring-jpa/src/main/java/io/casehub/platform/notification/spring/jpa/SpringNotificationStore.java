package io.casehub.platform.notification.spring.jpa;

import io.casehub.platform.api.notification.AllNotificationsRead;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationCreated;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStatusChanged;
import io.casehub.platform.api.notification.NotificationStore;
import io.casehub.platform.notification.jpa.NotificationEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class SpringNotificationStore implements NotificationStore {

    private final NotificationEntityRepository repo;
    private final ApplicationEventPublisher events;

    public SpringNotificationStore(NotificationEntityRepository repo,
                                   ApplicationEventPublisher events) {
        this.repo = repo;
        this.events = events;
    }

    @Override
    @Transactional
    public Notification store(NotificationInput input) {
        NotificationEntity entity = NotificationEntity.fromInput(input);
        repo.saveAndFlush(entity);
        Notification notification = entity.toNotification();
        events.publishEvent(new NotificationCreated(notification));
        return notification;
    }

    @Override
    @Transactional
    public List<Notification> storeAll(List<NotificationInput> inputs) {
        List<Notification> notifications = new ArrayList<>(inputs.size());
        for (NotificationInput input : inputs) {
            NotificationEntity entity = NotificationEntity.fromInput(input);
            repo.saveAndFlush(entity);
            Notification notification = entity.toNotification();
            notifications.add(notification);
            events.publishEvent(new NotificationCreated(notification));
        }
        return notifications;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPage find(NotificationQuery query) {
        int fetchLimit = query.limit() + 1;
        var pageable = PageRequest.of(0, fetchLimit);

        List<NotificationEntity> entities;
        if (query.cursor() != null) {
            CursorValue cursor = decodeCursor(query.cursor());
            if (cursor != null) {
                entities = repo.findByUserAndTenantAfterCursor(
                        query.userId(), query.tenancyId(),
                        cursor.createdAt, cursor.id, pageable);
            } else {
                entities = findWithoutCursor(query, pageable);
            }
        } else {
            entities = findWithoutCursor(query, pageable);
        }

        boolean hasMore = entities.size() > query.limit();
        List<NotificationEntity> pageEntities = hasMore
                ? entities.subList(0, query.limit())
                : entities;

        List<Notification> notifications = pageEntities.stream()
                .map(NotificationEntity::toNotification)
                .toList();

        String nextCursor = null;
        if (hasMore && !pageEntities.isEmpty()) {
            NotificationEntity last = pageEntities.getLast();
            nextCursor = encodeCursor(last.createdAt, last.id);
        }
        return new NotificationPage(notifications, nextCursor);
    }

    private List<NotificationEntity> findWithoutCursor(NotificationQuery query, PageRequest pageable) {
        if (query.status() != null && query.category() != null) {
            return repo.findByUserAndTenantAndStatusAndCategory(
                    query.userId(), query.tenancyId(), query.status(), query.category(), pageable);
        } else if (query.status() != null) {
            return repo.findByUserAndTenantAndStatus(
                    query.userId(), query.tenancyId(), query.status(), pageable);
        } else if (query.category() != null) {
            return repo.findByUserAndTenantAndCategory(
                    query.userId(), query.tenancyId(), query.category(), pageable);
        } else {
            return repo.findByUserAndTenant(query.userId(), query.tenancyId(), pageable);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(String userId, String tenancyId) {
        return repo.countByUserIdAndTenancyIdAndStatus(userId, tenancyId, NotificationStatus.UNREAD);
    }

    @Override
    @Transactional
    public Optional<Notification> markRead(String id, String userId, String tenancyId) {
        return repo.findByIdAndUserIdAndTenancyIdAndStatusNot(id, userId, tenancyId, NotificationStatus.DISMISSED)
                .map(entity -> {
                    NotificationStatus previousStatus = entity.status;
                    entity.status = NotificationStatus.READ;
                    entity.readAt = Instant.now();
                    repo.save(entity);
                    Notification notification = entity.toNotification();
                    events.publishEvent(new NotificationStatusChanged(notification, previousStatus));
                    return notification;
                });
    }

    @Override
    @Transactional
    public Optional<Notification> dismiss(String id, String userId, String tenancyId) {
        return repo.findByIdAndUserIdAndTenancyIdAndStatusNot(id, userId, tenancyId, NotificationStatus.DISMISSED)
                .map(entity -> {
                    NotificationStatus previousStatus = entity.status;
                    entity.status = NotificationStatus.DISMISSED;
                    entity.dismissedAt = Instant.now();
                    repo.save(entity);
                    Notification notification = entity.toNotification();
                    events.publishEvent(new NotificationStatusChanged(notification, previousStatus));
                    return notification;
                });
    }

    @Override
    @Transactional
    public int markAllRead(String userId, String tenancyId) {
        int count = repo.markAllRead(userId, tenancyId,
                NotificationStatus.UNREAD, NotificationStatus.READ, Instant.now());
        if (count > 0) {
            events.publishEvent(new AllNotificationsRead(userId, tenancyId, count));
        }
        return count;
    }

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
            return null;
        }
    }

    private record CursorValue(Instant createdAt, String id) {}
}
