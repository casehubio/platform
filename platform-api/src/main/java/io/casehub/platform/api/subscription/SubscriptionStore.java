package io.casehub.platform.api.subscription;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Blocking subscription persistence SPI. Implementations must provide both this interface
 * and {@link ReactiveSubscriptionStore} natively — no bridge pattern.
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
 * <p><strong>User Ownership Enforcement:</strong> {@code findById}, {@code update}, and
 * {@code delete} enforce user-level ownership via {@code userId} parameter.
 * Implementations WHERE clause includes {@code user_id = ? AND tenancy_id = ?}, making
 * authorization structural at the SPI boundary.
 *
 * <p><strong>Single Event Type:</strong> Each subscription matches exactly one event
 * type. Multi-type subscriptions require multiple subscription records.
 */
public interface SubscriptionStore {

    /**
     * Store a new subscription.
     *
     * <p>Generates UUID v7 id, captures {@code createdAt} and {@code updatedAt} timestamps.
     * Returns the persisted {@link Subscription}.
     *
     * <p>Fires {@link SubscriptionCreated} event (non-no-op implementations only).
     *
     * @param input subscription to store
     * @return the persisted subscription with generated id and timestamps
     */
    Subscription store(SubscriptionInput input);

    /**
     * Find a subscription by id.
     *
     * <p>User ownership enforced: returns empty if subscription not found, wrong tenant,
     * or wrong user. No information leak — empty is the same regardless of reason.
     *
     * @param id        subscription id
     * @param userId    subscription owner (authorization check)
     * @param tenancyId tenant isolation (authorization check)
     * @return subscription if found and authorized, empty otherwise
     */
    Optional<Subscription> findById(String id, String userId, String tenancyId);

    /**
     * Query subscriptions with cursor-based pagination.
     *
     * <p>Results ordered by {@code (createdAt DESC, id DESC)} — newest first, UUID v7
     * id breaks ties for same-timestamp subscriptions.
     *
     * <p>Cursor is opaque — encoding is implementation-owned. JPA uses keyset pagination
     * encoding {@code (createdAt, id)}; in-memory may use different scheme.
     *
     * @param query query parameters (user, tenant, optional enabled filter, cursor, limit)
     * @return page of subscriptions with optional next cursor
     */
    SubscriptionPage find(SubscriptionQuery query);

    /**
     * Update a subscription with partial changes. Only non-null fields in {@code update}
     * are applied; null fields remain unchanged.
     *
     * <p>Sets new {@code updatedAt} timestamp.
     *
     * <p>User ownership enforced: returns empty if subscription not found, wrong tenant,
     * or wrong user. No information leak — empty is the same regardless of reason.
     *
     * <p>Fires {@link SubscriptionUpdated} event on success
     * (non-no-op implementations only).
     *
     * @param id        subscription id
     * @param userId    subscription owner (authorization check)
     * @param tenancyId tenant isolation (authorization check)
     * @param update    partial update (null fields unchanged)
     * @return updated subscription with new {@code updatedAt}, or empty if not found/unauthorized
     */
    Optional<Subscription> update(String id, String userId, String tenancyId, SubscriptionUpdate update);

    /**
     * Delete a subscription.
     *
     * <p>User ownership enforced: returns false if subscription not found, wrong tenant,
     * or wrong user. No information leak — false is the same regardless of reason.
     *
     * <p>Fires {@link SubscriptionDeleted} event on success
     * (non-no-op implementations only).
     *
     * @param id        subscription id
     * @param userId    subscription owner (authorization check)
     * @param tenancyId tenant isolation (authorization check)
     * @return true if deleted, false if not found/unauthorized
     */
    boolean delete(String id, String userId, String tenancyId);

    /**
     * Stream all enabled subscriptions across all users and tenants. Used by subscription
     * matcher to evaluate incoming events.
     *
     * <p><strong>No tenant isolation</strong> — returns enabled subscriptions for all tenants.
     * Matcher must apply tenancy filtering based on event POJO properties.
     *
     * <p>Stream must be closed by caller to release resources. For large datasets,
     * implementations may batch-fetch results.
     *
     * @return stream of all enabled subscriptions (caller must close)
     */
    Stream<Subscription> findAllEnabled();
}
