package io.casehub.platform.subscription.jpa;

import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Blocking subscription store backed by Hibernate Reactive via Vert.x context.
 * Delegates to {@link JpaReactiveSubscriptionStore} — the reactive implementation
 * does the actual I/O. This wrapper creates a Vert.x duplicated context and
 * converts reactive to blocking for worker-thread callers.
 *
 * <p>This is NOT the bridge anti-pattern: the I/O is reactive (Vert.x PG client),
 * blocking is just a calling convention.
 */
@ApplicationScoped
public class JpaSubscriptionStore implements SubscriptionStore {

    @Inject
    JpaReactiveSubscriptionStore reactiveStore;

    @Inject
    Vertx vertx;

    @Override
    public Subscription store(SubscriptionInput input) {
        return execute(() -> reactiveStore.store(input));
    }

    @Override
    public Optional<Subscription> findById(String id, String userId, String tenancyId) {
        return execute(() -> reactiveStore.findById(id, userId, tenancyId));
    }

    @Override
    public SubscriptionPage find(SubscriptionQuery query) {
        return execute(() -> reactiveStore.find(query));
    }

    @Override
    public Optional<Subscription> update(String id, String userId, String tenancyId, SubscriptionUpdate update) {
        return execute(() -> reactiveStore.update(id, userId, tenancyId, update));
    }

    @Override
    public boolean delete(String id, String userId, String tenancyId) {
        return execute(() -> reactiveStore.delete(id, userId, tenancyId));
    }

    @Override
    public Stream<Subscription> findAllEnabled() {
        return execute(() -> reactiveStore.findAllEnabled()
                .collect().asList()
                .map(list -> list.stream()))
                .onClose(() -> {});
    }

    /**
     * Execute a reactive operation in a Vert.x duplicated context and block for the result.
     * Follows the pattern from {@code JpaNotificationStore.execute()}.
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
