package io.casehub.platform.subscription;

import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.casehub.platform.api.util.UUIDv7;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * No-op {@link SubscriptionStore} — active when no backend module is on the classpath.
 *
 * <p>{@link #store(SubscriptionInput)} returns a structurally valid {@link Subscription}
 * record (UUID v7 id, current timestamps) so callers that use the return value get valid
 * data. All queries return empty. All mutations return empty/false. {@link #findAllEnabled()}
 * returns empty stream.
 *
 * <p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link SubscriptionStore} implementation on the classpath, per the
 * {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpSubscriptionStore implements SubscriptionStore {

    @Override
    public Subscription store(final SubscriptionInput input) {
        return toSubscription(input);
    }

    @Override
    public Optional<Subscription> findById(final String id, final String ownerId, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public SubscriptionPage find(final SubscriptionQuery query) {
        return new SubscriptionPage(List.of(), null);
    }

    @Override
    public Optional<Subscription> update(final String id, final String ownerId, final String tenancyId, final SubscriptionUpdate update) {
        return Optional.empty();
    }

    @Override
    public boolean delete(final String id, final String ownerId, final String tenancyId) {
        return false;
    }

    @Override
    public Stream<Subscription> findAllEnabled() {
        return Stream.empty();
    }

    /**
     * Convert {@link SubscriptionInput} to a structurally valid {@link Subscription}.
     * Generates UUID v7 id, sets current timestamps.
     *
     * @param input subscription input
     * @return subscription with generated id and timestamps
     */
    private Subscription toSubscription(final SubscriptionInput input) {
        final var now = Instant.now();
        return new Subscription(
                UUIDv7.generate(),
                input.ownerId(),
                input.tenancyId(),
                input.name(),
                input.eventType(),
                input.constraints(),
                input.targets(),
                input.includeActor(),
                input.template(),
                input.enabled(),
                input.scope(),
                now,
                now
        );
    }
}
