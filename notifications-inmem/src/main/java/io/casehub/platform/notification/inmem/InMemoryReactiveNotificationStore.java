package io.casehub.platform.notification.inmem;

import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.ReactiveNotificationStore;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * Reactive {@link ReactiveNotificationStore} delegating to {@link InMemoryNotificationStore}.
 *
 * <p>Tier 4 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * JPA (Tier 2) and NoOp (Tier 1) when on the classpath.
 *
 * <p>Delegates to the blocking store without {@code runSubscriptionOn()} because
 * {@link InMemoryNotificationStore} uses {@link java.util.concurrent.ConcurrentHashMap},
 * which is non-blocking and safe on the event loop.
 *
 * <p>This is NOT the bridge anti-pattern — both blocking and reactive SPIs are implemented
 * natively. The delegation is an implementation detail (sharing the same in-memory store),
 * not a workaround for missing reactive support.
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryReactiveNotificationStore implements ReactiveNotificationStore {

    private final InMemoryNotificationStore delegate;

    @Inject
    public InMemoryReactiveNotificationStore(InMemoryNotificationStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public Uni<Notification> store(NotificationInput input) {
        return Uni.createFrom().item(() -> delegate.store(input));
    }

    @Override
    public Uni<List<Notification>> storeAll(List<NotificationInput> inputs) {
        return Uni.createFrom().item(() -> delegate.storeAll(inputs));
    }

    @Override
    public Uni<NotificationPage> find(NotificationQuery query) {
        return Uni.createFrom().item(() -> delegate.find(query));
    }

    @Override
    public Uni<Long> unreadCount(String userId, String tenancyId) {
        return Uni.createFrom().item(() -> delegate.unreadCount(userId, tenancyId));
    }

    @Override
    public Uni<Optional<Notification>> markRead(String id, String userId, String tenancyId) {
        return Uni.createFrom().item(() -> delegate.markRead(id, userId, tenancyId));
    }

    @Override
    public Uni<Optional<Notification>> dismiss(String id, String userId, String tenancyId) {
        return Uni.createFrom().item(() -> delegate.dismiss(id, userId, tenancyId));
    }

    @Override
    public Uni<Integer> markAllRead(String userId, String tenancyId) {
        return Uni.createFrom().item(() -> delegate.markAllRead(userId, tenancyId));
    }
}
