package io.casehub.platform.notification;

import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.ReactiveNotificationStore;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * No-op {@link ReactiveNotificationStore} — active when no backend module is on the classpath.
 *
 * <p>Delegates to {@link NoOpNotificationStore} — the no-op does no I/O, so delegation is
 * free (no thread hop, no {@code runSubscriptionOn}). Operations run on the caller's thread.
 *
 * <p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link ReactiveNotificationStore} implementation on the classpath, per the
 * {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpReactiveNotificationStore implements ReactiveNotificationStore {

    private final NoOpNotificationStore delegate;

    @Inject
    public NoOpReactiveNotificationStore(final NoOpNotificationStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public Uni<Notification> store(final NotificationInput input) {
        return Uni.createFrom().item(() -> delegate.store(input));
    }

    @Override
    public Uni<List<Notification>> storeAll(final List<NotificationInput> inputs) {
        return Uni.createFrom().item(() -> delegate.storeAll(inputs));
    }

    @Override
    public Uni<NotificationPage> find(final NotificationQuery query) {
        return Uni.createFrom().item(() -> delegate.find(query));
    }

    @Override
    public Uni<Long> unreadCount(final String userId, final String tenancyId) {
        return Uni.createFrom().item(() -> delegate.unreadCount(userId, tenancyId));
    }

    @Override
    public Uni<Optional<Notification>> markRead(final String id, final String userId, final String tenancyId) {
        return Uni.createFrom().item(() -> delegate.markRead(id, userId, tenancyId));
    }

    @Override
    public Uni<Optional<Notification>> dismiss(final String id, final String userId, final String tenancyId) {
        return Uni.createFrom().item(() -> delegate.dismiss(id, userId, tenancyId));
    }

    @Override
    public Uni<Integer> markAllRead(final String userId, final String tenancyId) {
        return Uni.createFrom().item(() -> delegate.markAllRead(userId, tenancyId));
    }
}
