package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationApi;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class NotificationService implements NotificationApi {

    private final NotificationStore store;
    private final CurrentPrincipal principal;

    @Inject
    public NotificationService(NotificationStore store, CurrentPrincipal principal) {
        this.store = store;
        this.principal = principal;
    }

    @Override
    public NotificationPage list(NotificationStatus status, String category, String cursor, Integer limit) {
        return store.find(new NotificationQuery(
                principal.actorId(), principal.tenancyId(),
                status, category, cursor, limit != null ? limit : 25));
    }

    @Override
    public Map<String, Long> unreadCount() {
        return Map.of("count", store.unreadCount(principal.actorId(), principal.tenancyId()));
    }

    @Override
    public Optional<Notification> markRead(String id) {
        return store.markRead(id, principal.actorId(), principal.tenancyId());
    }

    @Override
    public Optional<Notification> dismiss(String id) {
        return store.dismiss(id, principal.actorId(), principal.tenancyId());
    }

    @Override
    public Map<String, Integer> markAllRead() {
        return Map.of("count", store.markAllRead(principal.actorId(), principal.tenancyId()));
    }
}
