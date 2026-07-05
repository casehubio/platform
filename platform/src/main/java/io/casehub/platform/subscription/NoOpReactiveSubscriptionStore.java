package io.casehub.platform.subscription;

import io.casehub.platform.api.subscription.ReactiveSubscriptionStore;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.quarkus.arc.DefaultBean;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * No-op {@link ReactiveSubscriptionStore} — active when no backend module is on the classpath.
 *
 * <p>Delegates to {@link NoOpSubscriptionStore} — the no-op does no I/O, so delegation is
 * free (no thread hop, no {@code runSubscriptionOn}). Operations run on the caller's thread.
 *
 * <p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link ReactiveSubscriptionStore} implementation on the classpath, per the
 * {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpReactiveSubscriptionStore implements ReactiveSubscriptionStore {

    private final NoOpSubscriptionStore delegate;

    @Inject
    public NoOpReactiveSubscriptionStore(final NoOpSubscriptionStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public Uni<Subscription> store(final SubscriptionInput input) {
        return Uni.createFrom().item(() -> delegate.store(input));
    }

    @Override
    public Uni<Optional<Subscription>> findById(final String id, final String userId, final String tenancyId) {
        return Uni.createFrom().item(() -> delegate.findById(id, userId, tenancyId));
    }

    @Override
    public Uni<SubscriptionPage> find(final SubscriptionQuery query) {
        return Uni.createFrom().item(() -> delegate.find(query));
    }

    @Override
    public Uni<Optional<Subscription>> update(final String id, final String userId, final String tenancyId, final SubscriptionUpdate update) {
        return Uni.createFrom().item(() -> delegate.update(id, userId, tenancyId, update));
    }

    @Override
    public Uni<Boolean> delete(final String id, final String userId, final String tenancyId) {
        return Uni.createFrom().item(() -> delegate.delete(id, userId, tenancyId));
    }

    @Override
    public Multi<Subscription> findAllEnabled() {
        return Multi.createFrom().items(delegate.findAllEnabled());
    }
}
