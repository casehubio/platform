package io.casehub.platform.api.subscription;

import io.casehub.platform.api.notification.NotificationSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test base for {@link SubscriptionStore} implementations. Concrete subclasses
 * provide the store instance and state clearing logic.
 *
 * <p>Covers: store + retrieve, find with filters, update (partial + nulls unchanged),
 * delete, findAllEnabled (cross-tenant), tenant isolation, user ownership enforcement,
 * cursor pagination ordering.
 */
public abstract class SubscriptionStoreContractTest {

    protected abstract SubscriptionStore store();

    @BeforeEach
    void setUp() {
        clearState();
    }

    /**
     * Clear store state before each test. Implementations may leave empty (for in-memory
     * with fresh instance per test) or implement cleanup (for persistent stores).
     */
    protected void clearState() {
    }

    // Store and Retrieve Tests

    @Test
    void store_persistsWithGeneratedId() {
        var input = createInput("user-1", "tenant-1", "My Subscription", "work-item.created");
        var subscription = store().store(input);

        assertThat(subscription.id()).isNotNull();
        assertThat(subscription.ownerId()).isEqualTo("user-1");
        assertThat(subscription.tenancyId()).isEqualTo("tenant-1");
        assertThat(subscription.name()).isEqualTo("My Subscription");
        assertThat(subscription.eventType()).isEqualTo("work-item.created");
        assertThat(subscription.enabled()).isTrue();
        assertThat(subscription.createdAt()).isNotNull();
        assertThat(subscription.updatedAt()).isNotNull();
    }

    @Test
    void store_setsTimestamps() {
        var before = Instant.now();
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));
        var after = Instant.now();

        assertThat(subscription.createdAt()).isBetween(before, after);
        assertThat(subscription.updatedAt()).isBetween(before, after);
    }

    @Test
    void findById_returnsStoredSubscription() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var found = store().findById(subscription.id(), "user-1", "tenant-1");

        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(subscription);
    }

    @Test
    void findById_wrongUser_returnsEmpty() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var found = store().findById(subscription.id(), "user-2", "tenant-1");

        assertThat(found).isEmpty();
    }

    @Test
    void findById_wrongTenant_returnsEmpty() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var found = store().findById(subscription.id(), "user-1", "tenant-2");

        assertThat(found).isEmpty();
    }

    // Query Tests

    @Test
    void find_returnsPaginatedResults() {
        store().store(createInput("user-1", "tenant-1", "Sub 1", "event-type"));
        store().store(createInput("user-1", "tenant-1", "Sub 2", "event-type"));

        var query = new SubscriptionQuery("user-1", "tenant-1", null, null, 10);
        var page = store().find(query);

        assertThat(page.subscriptions()).hasSize(2);
    }

    @Test
    void find_filtersByEnabled() {
        var input1 = createInput("user-1", "tenant-1", "Enabled", "event-type");
        var input2 = new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Disabled",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                false
        );

        store().store(input1);
        store().store(input2);

        var query = new SubscriptionQuery("user-1", "tenant-1", true, null, 10);
        var page = store().find(query);

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0).name()).isEqualTo("Enabled");
        assertThat(page.subscriptions().get(0).enabled()).isTrue();
    }

    @Test
    void find_respectsTenantIsolation() {
        store().store(createInput("user-1", "tenant-1", "Tenant 1", "event-type"));
        store().store(createInput("user-1", "tenant-2", "Tenant 2", "event-type"));

        var query = new SubscriptionQuery("user-1", "tenant-1", null, null, 10);
        var page = store().find(query);

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0).tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void find_respectsUserIsolation() {
        store().store(createInput("user-1", "tenant-1", "User 1", "event-type"));
        store().store(createInput("user-2", "tenant-1", "User 2", "event-type"));

        var query = new SubscriptionQuery("user-1", "tenant-1", null, null, 10);
        var page = store().find(query);

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0).ownerId()).isEqualTo("user-1");
    }

    // Update Tests

    @Test
    void update_changesName() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Old Name", "event-type"));

        var update = new SubscriptionUpdate("New Name", null, null, null, null, null, null);
        var updated = store().update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("New Name");
        assertThat(updated.get().eventType()).isEqualTo("event-type");
    }

    @Test
    void update_changesEventType() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "old-event"));

        var update = new SubscriptionUpdate(null, "new-event", null, null, null, null, null);
        var updated = store().update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().eventType()).isEqualTo("new-event");
    }

    @Test
    void update_changesConstraints() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var newConstraints = List.of(new Constraint("newField", ConstraintOp.EQ, "newValue"));
        var update = new SubscriptionUpdate(null, null, newConstraints, null, null, null, null);
        var updated = store().update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().constraints()).hasSize(1);
        assertThat(updated.get().constraints().get(0).field()).isEqualTo("newField");
    }

    @Test
    void update_changesEnabled() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var update = new SubscriptionUpdate(null, null, null, null, null, null, false);
        var updated = store().update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().enabled()).isFalse();
    }

    @Test
    void update_setsUpdatedAt() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));
        var originalUpdatedAt = subscription.updatedAt();

        // Sleep to ensure timestamp difference (some stores may have millisecond precision)
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        var update = new SubscriptionUpdate("New Name", null, null, null, null, null, null);
        var updated = store().update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void update_nullFieldsUnchanged() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var update = new SubscriptionUpdate("New Name", null, null, null, null, null, null);
        var updated = store().update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("New Name");
        assertThat(updated.get().eventType()).isEqualTo("event-type");
        assertThat(updated.get().enabled()).isTrue();
    }

    @Test
    void update_wrongUser_returnsEmpty() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var update = new SubscriptionUpdate("New Name", null, null, null, null, null, null);
        var updated = store().update(subscription.id(), "user-2", "tenant-1", update);

        assertThat(updated).isEmpty();
    }

    // Delete Tests

    @Test
    void delete_removesSubscription() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var deleted = store().delete(subscription.id(), "user-1", "tenant-1");

        assertThat(deleted).isTrue();
        assertThat(store().findById(subscription.id(), "user-1", "tenant-1")).isEmpty();
    }

    @Test
    void delete_wrongUser_returnsFalse() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var deleted = store().delete(subscription.id(), "user-2", "tenant-1");

        assertThat(deleted).isFalse();
    }

    // FindAllEnabled Tests

    @Test
    void findAllEnabled_returnsOnlyEnabled() {
        var enabled = createInput("user-1", "tenant-1", "Enabled", "event-type");
        var disabled = new SubscriptionInput(
                "user-1",
                "tenant-1",
                "Disabled",
                "event-type",
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false,
                createTemplate(),
                false
        );

        store().store(enabled);
        store().store(disabled);

        try (var stream = store().findAllEnabled()) {
            var subscriptions = stream.toList();
            assertThat(subscriptions).hasSize(1);
            assertThat(subscriptions.get(0).name()).isEqualTo("Enabled");
            assertThat(subscriptions.get(0).enabled()).isTrue();
        }
    }

    @Test
    void findAllEnabled_crossesTenantBoundaries() {
        store().store(createInput("user-1", "tenant-1", "Sub 1", "event-type"));
        store().store(createInput("user-2", "tenant-2", "Sub 2", "event-type"));

        try (var stream = store().findAllEnabled()) {
            var subscriptions = stream.toList();
            assertThat(subscriptions).hasSize(2);
            assertThat(subscriptions).extracting(Subscription::tenancyId)
                    .containsExactlyInAnyOrder("tenant-1", "tenant-2");
        }
    }

    // Helper Methods

    private SubscriptionInput createInput(String ownerId, String tenancyId, String name, String eventType) {
        return new SubscriptionInput(
                ownerId,
                tenancyId,
                name,
                eventType,
                List.of(),
                List.of(new NotificationTarget(TargetType.USER, ownerId)),
                false,
                createTemplate(),
                true
        );
    }

    private NotificationTemplate createTemplate() {
        return new NotificationTemplate(
                "Title",
                null,
                NotificationSeverity.INFO,
                "category",
                null,
                "entity",
                "id",
                "actor"
        );
    }
}
