package io.casehub.platform.api.subscription;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import java.util.Optional;

/**
 * Reactive subscription persistence SPI. Implementations must provide both this interface
 * and {@link SubscriptionStore} natively — no bridge pattern.
 *
 * <p><strong>CDI Events:</strong> Non-no-op implementations must fire:
 * <ul>
 *   <li>{@link SubscriptionCreated} — after {@code store()}</li>
 *   <li>{@link SubscriptionUpdated} — after {@code update()}</li>
 *   <li>{@link SubscriptionDeleted} — after {@code delete()}</li>
 * </ul>
 *
 * <p>The no-op {@code @DefaultBean} implementation must NOT fire events.
 *
 * <p><strong>Scope-Dependent Authorisation:</strong> For {@link SubscriptionScope#USER USER}
 * scope subscriptions, {@code ownerId} is enforced at the SPI boundary. For
 * {@link SubscriptionScope#SYSTEM SYSTEM} scope subscriptions, only {@code tenancy_id}
 * is enforced — admin authorisation is enforced at the REST layer. Implementations use
 * OR-disjunction queries: {@code WHERE tenancy_id = ? AND (owner_id = ? OR scope = 'SYSTEM')}.
 *
 * <p><strong>Single Event Type:</strong> Each subscription matches exactly one event
 * type. Multi-type subscriptions require multiple subscription records.
 */
public interface ReactiveSubscriptionStore {

    Uni<Subscription> store(SubscriptionInput input);

    Uni<Optional<Subscription>> findById(String id, String ownerId, String tenancyId);

    Uni<SubscriptionPage> find(SubscriptionQuery query);

    Uni<Optional<Subscription>> update(String id, String ownerId, String tenancyId, SubscriptionUpdate update);

    Uni<Boolean> delete(String id, String ownerId, String tenancyId);

    Multi<Subscription> findAllEnabled();
}
