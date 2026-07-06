package io.casehub.platform.subscription.inmem;

import io.casehub.platform.api.notification.UUIDv7;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionCreated;
import io.casehub.platform.api.subscription.SubscriptionDeleted;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionPage;
import io.casehub.platform.api.subscription.SubscriptionQuery;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionUpdate;
import io.casehub.platform.api.subscription.SubscriptionUpdated;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Volatile in-memory {@link SubscriptionStore}.
 *
 * <p>Tier 4 in the CDI priority ladder — {@code @Alternative @Priority(100)} beats
 * NoOp (Tier 1) when on the classpath.
 *
 * <p>Thread-safe. Data is ephemeral (lost on restart). Suitable for tests and
 * zero-config ephemeral single-node installs.
 *
 * <h2>CDI Events</h2>
 * <p>Fires {@link SubscriptionCreated}, {@link SubscriptionUpdated}, and
 * {@link SubscriptionDeleted} via {@code fireAsync()} after successful operations.
 * The package-private no-arg constructor (used by CDI proxy and unit tests) leaves
 * event fields null; null guards in methods prevent NPE in those paths.
 */
@Alternative
@Priority(100)
@ApplicationScoped
public class InMemorySubscriptionStore implements SubscriptionStore {

    private final ConcurrentHashMap<String, Subscription> store = new ConcurrentHashMap<>();
    private final Event<SubscriptionCreated> subscriptionCreatedEvent;
    private final Event<SubscriptionUpdated> subscriptionUpdatedEvent;
    private final Event<SubscriptionDeleted> subscriptionDeletedEvent;

    @Inject
    public InMemorySubscriptionStore(
            Event<SubscriptionCreated> subscriptionCreatedEvent,
            Event<SubscriptionUpdated> subscriptionUpdatedEvent,
            Event<SubscriptionDeleted> subscriptionDeletedEvent
    ) {
        this.subscriptionCreatedEvent = subscriptionCreatedEvent;
        this.subscriptionUpdatedEvent = subscriptionUpdatedEvent;
        this.subscriptionDeletedEvent = subscriptionDeletedEvent;
    }

    /** Used by CDI proxy subclass and unit tests. */
    InMemorySubscriptionStore() {
        this(null, null, null);
    }

    @Override
    public Subscription store(SubscriptionInput input) {
        var subscription = toSubscription(input);
        store.put(subscription.id(), subscription);
        fireSubscriptionCreated(subscription);
        return subscription;
    }

    @Override
    public Optional<Subscription> findById(String id, String ownerId, String tenancyId) {
        return Optional.ofNullable(store.get(id))
                .filter(s -> s.ownerId().equals(ownerId))
                .filter(s -> s.tenancyId().equals(tenancyId));
    }

    @Override
    public SubscriptionPage find(SubscriptionQuery query) {
        var comparator = Comparator.comparing(Subscription::createdAt)
                .thenComparing(Subscription::id)
                .reversed();

        var filtered = store.values().stream()
                .filter(s -> s.ownerId().equals(query.ownerId()))
                .filter(s -> s.tenancyId().equals(query.tenancyId()))
                .filter(s -> query.enabled() == null || s.enabled() == query.enabled())
                .filter(s -> matchesCursor(s, query.cursor()))
                .sorted(comparator)
                .limit(query.limit() + 1)
                .toList();

        boolean hasMore = filtered.size() > query.limit();
        var subscriptions = hasMore ? filtered.subList(0, query.limit()) : filtered;
        String nextCursor = hasMore ? encodeCursor(subscriptions.get(subscriptions.size() - 1)) : null;

        return new SubscriptionPage(subscriptions, nextCursor);
    }

    @Override
    public Optional<Subscription> update(String id, String ownerId, String tenancyId, SubscriptionUpdate update) {
        var result = new Object() {
            Subscription updated = null;
            Subscription previous = null;
        };

        store.compute(id, (key, subscription) -> {
            if (subscription == null
                    || !subscription.ownerId().equals(ownerId)
                    || !subscription.tenancyId().equals(tenancyId)) {
                return subscription; // No change
            }

            result.previous = subscription;
            result.updated = applyUpdate(subscription, update);
            return result.updated;
        });

        if (result.updated != null) {
            fireSubscriptionUpdated(result.updated, result.previous);
        }
        return Optional.ofNullable(result.updated);
    }

    @Override
    public boolean delete(String id, String ownerId, String tenancyId) {
        var result = new Object() {
            Subscription deleted = null;
        };

        store.compute(id, (key, subscription) -> {
            if (subscription != null
                    && subscription.ownerId().equals(ownerId)
                    && subscription.tenancyId().equals(tenancyId)) {
                result.deleted = subscription;
                return null; // Remove from map
            }
            return subscription; // No change
        });

        if (result.deleted != null) {
            fireSubscriptionDeleted(result.deleted);
            return true;
        }
        return false;
    }

    @Override
    public Stream<Subscription> findAllEnabled() {
        return store.values().stream()
                .filter(Subscription::enabled);
    }

    // Private Methods

    private Subscription toSubscription(SubscriptionInput input) {
        var now = Instant.now();
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
                now,  // createdAt
                now   // updatedAt
        );
    }

    private Subscription applyUpdate(Subscription subscription, SubscriptionUpdate update) {
        return new Subscription(
                subscription.id(),
                subscription.ownerId(),
                subscription.tenancyId(),
                update.name() != null ? update.name() : subscription.name(),
                update.eventType() != null ? update.eventType() : subscription.eventType(),
                update.constraints() != null ? update.constraints() : subscription.constraints(),
                update.targets() != null ? update.targets() : subscription.targets(),
                update.includeActor() != null ? update.includeActor() : subscription.includeActor(),
                update.template() != null ? update.template() : subscription.template(),
                update.enabled() != null ? update.enabled() : subscription.enabled(),
                subscription.createdAt(),
                Instant.now()  // updatedAt
        );
    }

    private boolean matchesCursor(Subscription s, String cursor) {
        if (cursor == null) return true;

        var decoded = decodeCursor(cursor);
        if (decoded == null) return true; // Invalid cursor — include all

        long cursorTimestamp = decoded.timestampMs;
        String cursorId = decoded.id;

        long subscriptionTimestamp = s.createdAt().toEpochMilli();

        // Cursor pagination: (createdAt DESC, id DESC)
        // Cursor points to the LAST item on the previous page
        // Include if (createdAt, id) is STRICTLY LESS than cursor
        // Lexicographic comparison: first by timestamp, then by id
        if (subscriptionTimestamp < cursorTimestamp) return true;
        if (subscriptionTimestamp > cursorTimestamp) return false;
        // Same timestamp — compare IDs (DESC order, so we want id < cursorId)
        return s.id().compareTo(cursorId) < 0;
    }

    private String encodeCursor(Subscription subscription) {
        long timestampMs = subscription.createdAt().toEpochMilli();
        String encoded = timestampMs + ":" + subscription.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.getBytes());
    }

    private CursorData decodeCursor(String cursor) {
        try {
            var decoded = new String(Base64.getUrlDecoder().decode(cursor));
            var parts = decoded.split(":", 2);
            if (parts.length != 2) return null;
            return new CursorData(Long.parseLong(parts[0]), parts[1]);
        } catch (Exception e) {
            return null;
        }
    }

    private void fireSubscriptionCreated(Subscription subscription) {
        if (subscriptionCreatedEvent != null) {
            subscriptionCreatedEvent.fireAsync(new SubscriptionCreated(subscription));
        }
    }

    private void fireSubscriptionUpdated(Subscription subscription, Subscription previous) {
        if (subscriptionUpdatedEvent != null) {
            subscriptionUpdatedEvent.fireAsync(
                    new SubscriptionUpdated(subscription, previous));
        }
    }

    private void fireSubscriptionDeleted(Subscription subscription) {
        if (subscriptionDeletedEvent != null) {
            subscriptionDeletedEvent.fireAsync(new SubscriptionDeleted(subscription));
        }
    }

    /**
     * Clears all stored subscriptions. For test use only.
     */
    public void clear() {
        store.clear();
    }

    private record CursorData(long timestampMs, String id) {}
}
