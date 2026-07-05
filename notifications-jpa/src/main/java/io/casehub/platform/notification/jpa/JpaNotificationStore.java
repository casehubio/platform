package io.casehub.platform.notification.jpa;

import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationStore;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Blocking notification store backed by Hibernate Reactive via Vert.x context.
 * Delegates to {@link JpaReactiveNotificationStore} — the reactive implementation
 * does the actual I/O. This wrapper creates a Vert.x duplicated context and
 * converts reactive to blocking for worker-thread callers.
 *
 * <p>This is NOT the bridge anti-pattern: the I/O is reactive (Vert.x PG client /
 * H2 reactive emulated), blocking is just a calling convention.
 */
@ApplicationScoped
public class JpaNotificationStore implements NotificationStore {

    @Inject
    JpaReactiveNotificationStore reactiveStore;

    @Inject
    Vertx vertx;

    @Override
    public Notification store(NotificationInput input) {
        return execute(() -> reactiveStore.store(input));
    }

    @Override
    public List<Notification> storeAll(List<NotificationInput> inputs) {
        return execute(() -> reactiveStore.storeAll(inputs));
    }

    @Override
    public NotificationPage find(NotificationQuery query) {
        return execute(() -> reactiveStore.find(query));
    }

    @Override
    public long unreadCount(String userId, String tenancyId) {
        return execute(() -> reactiveStore.unreadCount(userId, tenancyId));
    }

    @Override
    public Optional<Notification> markRead(String id, String userId, String tenancyId) {
        return execute(() -> reactiveStore.markRead(id, userId, tenancyId));
    }

    @Override
    public Optional<Notification> dismiss(String id, String userId, String tenancyId) {
        return execute(() -> reactiveStore.dismiss(id, userId, tenancyId));
    }

    @Override
    public int markAllRead(String userId, String tenancyId) {
        return execute(() -> reactiveStore.markAllRead(userId, tenancyId));
    }

    /**
     * Execute a reactive operation in a Vert.x duplicated context and block for the result.
     * Follows the pattern from {@code JpaAccessControlProvider.execute()}.
     */
    @SuppressWarnings("unchecked")
    private <T> T execute(Supplier<Uni<? extends T>> work) {
        Context context = VertxContext.getOrCreateDuplicatedContext(vertx);
        VertxContextSafetyToggle.setContextSafe(context, true);
        return (T) Uni.createFrom().deferred(work)
                .runSubscriptionOn(r -> context.runOnContext(v -> r.run()))
                .subscribeAsCompletionStage()
                .toCompletableFuture()
                .join();
    }
}
