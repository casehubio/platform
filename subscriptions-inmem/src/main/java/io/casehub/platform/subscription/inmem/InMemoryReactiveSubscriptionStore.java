package io.casehub.platform.subscription.inmem;

import io.casehub.platform.api.subscription.ReactiveSubscriptionStore;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Reactive {@link ReactiveSubscriptionStore} delegating to {@link InMemorySubscriptionStore}.
 *
 * <p>Tier 4 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * NoOp (Tier 1) when on the classpath.
 *
 * <p>Delegates to the blocking store without {@code runSubscriptionOn()} because
 * {@link InMemorySubscriptionStore} uses {@link java.util.concurrent.ConcurrentHashMap},
 * which is non-blocking and safe on the event loop.
 *
 * <p>This is NOT the bridge anti-pattern — both blocking and reactive SPIs are implemented
 * natively. The delegation is an implementation detail (sharing the same in-memory store),
 * not a workaround for missing reactive support.
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemoryReactiveSubscriptionStore implements ReactiveSubscriptionStore {

    private final InMemorySubscriptionStore delegate;

    @Inject
    public InMemoryReactiveSubscriptionStore(InMemorySubscriptionStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public Uni<Subscription> store(SubscriptionInput input) {
        return Uni.createFrom().item(() -> delegate.store(input));
    }

    @Override
    public Uni<Optional<Subscription>> findById(String id, String ownerId, String tenancyId) {
        return Uni.createFrom().item(() -> delegate.findById(id, ownerId, tenancyId));
    }

    @Override
    public Uni<SubscriptionPage> find(SubscriptionQuery query) {
        return Uni.createFrom().item(() -> delegate.find(query));
    }

    @Override
    public Uni<Optional<Subscription>> update(String id, String ownerId, String tenancyId, SubscriptionUpdate update) {
        return Uni.createFrom().item(() -> delegate.update(id, ownerId, tenancyId, update));
    }

    @Override
    public Uni<Boolean> delete(String id, String ownerId, String tenancyId) {
        return Uni.createFrom().item(() -> delegate.delete(id, ownerId, tenancyId));
    }

    @Override
    public Multi<Subscription> findAllEnabled() {
        return Multi.createFrom().items(delegate.findAllEnabled());
    }
}
