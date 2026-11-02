package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationApi;
import io.casehub.platform.api.notification.NotificationClicked;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationReplied;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStore;
import io.casehub.platform.api.notification.ReplyHint;
import io.casehub.platform.api.notification.ReplyRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class NotificationService implements NotificationApi {

    private final NotificationStore store;
    private final CurrentPrincipal principal;
    private final Event<NotificationClicked> clickedEvent;
    private final Event<NotificationReplied> repliedEvent;

    @Inject
    public NotificationService(NotificationStore store, CurrentPrincipal principal,
                               Event<NotificationClicked> clickedEvent,
                               Event<NotificationReplied> repliedEvent) {
        this.store = store;
        this.principal = principal;
        this.clickedEvent = clickedEvent;
        this.repliedEvent = repliedEvent;
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

    @Override
    public Optional<Notification> trackClick(String id) {
        var notification = store.findById(id, principal.actorId(), principal.tenancyId());
        notification.ifPresent(n -> clickedEvent.fireAsync(new NotificationClicked(n)));
        return notification;
    }

    @Override
    public Optional<Notification> reply(String id, ReplyRequest request) {
        var notification = store.findById(id, principal.actorId(), principal.tenancyId());
        if (notification.isEmpty()) {return Optional.empty();}
        var n = notification.get();
        if (n.status() == NotificationStatus.DISMISSED) {
            throw new IllegalArgumentException("Cannot reply to dismissed notification");
        }
        if (n.replyHint() == null) {
            throw new IllegalArgumentException("Notification does not accept replies");
        }
        String effectiveValue = validateAndNormalizeReply(n.replyHint(), request.value());
        repliedEvent.fireAsync(new NotificationReplied(n, effectiveValue));
        return notification;
    }

    private String validateAndNormalizeReply(ReplyHint hint, String value) {
        return switch (hint.type()) {
            case ACKNOWLEDGE -> null;
            case FREE_TEXT -> {
                if (value == null) {throw new IllegalArgumentException("Reply value required for FREE_TEXT");}
                yield value;
            }
            case OPTIONS -> {
                if (value == null || !hint.options().contains(value)) {
                    throw new IllegalArgumentException("Reply value must be one of: " + hint.options());
                }
                yield value;
            }
        };
    }
}
