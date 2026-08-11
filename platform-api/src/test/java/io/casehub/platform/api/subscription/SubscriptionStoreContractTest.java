package io.casehub.platform.api.subscription;

import io.casehub.platform.api.expression.ExpressionEvaluator;
import io.casehub.platform.api.expression.MvelExpressionEvaluator;
import io.casehub.platform.api.notification.NotificationSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
        assertThat(found.get())
                .usingRecursiveComparison()
                .withComparatorForType(
                        (a, b) -> Math.abs(ChronoUnit.MICROS.between(a, b)) <= 1 ? 0 : a.compareTo(b),
                        Instant.class)
                .isEqualTo(subscription);
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

        var query = new SubscriptionQuery("user-1", "tenant-1", null, null, null, 10);
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
                false,
                null
        );

        store().store(input1);
        store().store(input2);

        var query = new SubscriptionQuery("user-1", "tenant-1", null, true, null, 10);
        var page = store().find(query);

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0).name()).isEqualTo("Enabled");
        assertThat(page.subscriptions().get(0).enabled()).isTrue();
    }

    @Test
    void find_respectsTenantIsolation() {
        store().store(createInput("user-1", "tenant-1", "Tenant 1", "event-type"));
        store().store(createInput("user-1", "tenant-2", "Tenant 2", "event-type"));

        var query = new SubscriptionQuery("user-1", "tenant-1", null, null, null, 10);
        var page = store().find(query);

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0).tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void find_respectsUserIsolation() {
        store().store(createInput("user-1", "tenant-1", "User 1", "event-type"));
        store().store(createInput("user-2", "tenant-1", "User 2", "event-type"));

        var query = new SubscriptionQuery("user-1", "tenant-1", null, null, null, 10);
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
    void update_changesFilters() {
        var subscription = store().store(createInput("user-1", "tenant-1", "Name", "event-type"));

        var newFilters = List.of(
                (ExpressionEvaluator) new MvelExpressionEvaluator("newField == 'newValue'"));
        var update = new SubscriptionUpdate(null, null, newFilters, null, null, null, null);
        var updated = store().update(subscription.id(), "user-1", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().filters()).hasSize(1);
        assertThat(updated.get().filters().get(0).type()).isEqualTo("mvel");
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
                false,
                null
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


// SYSTEM Scope Tests

    @Test
    void store_systemScope_persistsScope() {
        var input        = createSystemInput("admin-1", "tenant-1", "System Alert", "alert.triggered");
        var subscription = store().store(input);

        assertThat(subscription.scope()).isEqualTo(SubscriptionScope.SYSTEM);
    }

    @Test
    void findById_systemScope_anyTenantUserCanRead() {
        var input        = createSystemInput("admin-1", "tenant-1", "System Alert", "alert.triggered");
        var subscription = store().store(input);

        var found = store().findById(subscription.id(), "user-2", "tenant-1");
        assertThat(found).isPresent();
        assertThat(found.get().scope()).isEqualTo(SubscriptionScope.SYSTEM);
    }

    @Test
    void findById_systemScope_crossTenantBlocked() {
        var input        = createSystemInput("admin-1", "tenant-1", "System Alert", "alert.triggered");
        var subscription = store().store(input);

        var found = store().findById(subscription.id(), "user-1", "tenant-2");
        assertThat(found).isEmpty();
    }

    @Test
    void findById_userScope_otherUserBlocked() {
        var input        = createInput("user-1", "tenant-1", "My Sub", "event");
        var subscription = store().store(input);

        var found = store().findById(subscription.id(), "user-2", "tenant-1");
        assertThat(found).isEmpty();
    }

    @Test
    void find_systemScope_returnsTenantWide() {
        store().store(createSystemInput("admin-1", "tenant-1", "Alert 1", "alert.a"));
        store().store(createSystemInput("admin-2", "tenant-1", "Alert 2", "alert.b"));
        store().store(createInput("user-1", "tenant-1", "My Sub", "event"));

        var query = new SubscriptionQuery(null, "tenant-1", SubscriptionScope.SYSTEM, null, null, 10);
        var page  = store().find(query);

        assertThat(page.subscriptions()).hasSize(2);
        assertThat(page.subscriptions()).allMatch(s -> s.scope() == SubscriptionScope.SYSTEM);
    }

    @Test
    void find_systemScope_respectsTenantIsolation() {
        store().store(createSystemInput("admin-1", "tenant-1", "Alert T1", "alert"));
        store().store(createSystemInput("admin-2", "tenant-2", "Alert T2", "alert"));

        var query = new SubscriptionQuery(null, "tenant-1", SubscriptionScope.SYSTEM, null, null, 10);
        var page  = store().find(query);

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0).tenancyId()).isEqualTo("tenant-1");
    }

    @Test
    void find_userScope_excludesSystemSubscriptions() {
        store().store(createSystemInput("admin-1", "tenant-1", "System Alert", "alert"));
        store().store(createInput("admin-1", "tenant-1", "My Sub", "event"));

        var query = new SubscriptionQuery("admin-1", "tenant-1", null, null, null, 10);
        var page  = store().find(query);

        assertThat(page.subscriptions()).hasSize(1);
        assertThat(page.subscriptions().get(0).scope()).isEqualTo(SubscriptionScope.USER);
        assertThat(page.subscriptions().get(0).name()).isEqualTo("My Sub");
    }

    @Test
    void update_systemScope_anyTenantUserCanUpdate() {
        var input        = createSystemInput("admin-1", "tenant-1", "Alert", "alert");
        var subscription = store().store(input);

        var update  = new SubscriptionUpdate("Updated Alert", null, null, null, null, null, null);
        var updated = store().update(subscription.id(), "user-2", "tenant-1", update);

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("Updated Alert");
        assertThat(updated.get().scope()).isEqualTo(SubscriptionScope.SYSTEM);
    }

    @Test
    void update_systemScope_crossTenantBlocked() {
        var input        = createSystemInput("admin-1", "tenant-1", "Alert", "alert");
        var subscription = store().store(input);

        var update  = new SubscriptionUpdate("Hacked", null, null, null, null, null, null);
        var updated = store().update(subscription.id(), "user-1", "tenant-2", update);

        assertThat(updated).isEmpty();
    }

    @Test
    void delete_systemScope_anyTenantUserCanDelete() {
        var input        = createSystemInput("admin-1", "tenant-1", "Alert", "alert");
        var subscription = store().store(input);

        var deleted = store().delete(subscription.id(), "user-2", "tenant-1");
        assertThat(deleted).isTrue();
    }

    @Test
    void delete_systemScope_crossTenantBlocked() {
        var input        = createSystemInput("admin-1", "tenant-1", "Alert", "alert");
        var subscription = store().store(input);

        var deleted = store().delete(subscription.id(), "user-1", "tenant-2");
        assertThat(deleted).isFalse();
    }

    @Test
    void findAllEnabled_includesSystemScope() {
        store().store(createInput("user-1", "tenant-1", "User Sub", "event"));
        store().store(createSystemInput("admin-1", "tenant-1", "System Sub", "alert"));

        try (var stream = store().findAllEnabled()) {
            var subscriptions = stream.toList();
            assertThat(subscriptions).hasSize(2);
            assertThat(subscriptions).extracting(Subscription::scope)
                                     .containsExactlyInAnyOrder(SubscriptionScope.USER, SubscriptionScope.SYSTEM);
        }
    }

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
                true,
                null
        );
    }

    private SubscriptionInput createSystemInput(String ownerId, String tenancyId, String name, String eventType) {
        return new SubscriptionInput(
                ownerId, tenancyId, name, eventType,
                List.of(),
                List.of(new NotificationTarget(TargetType.GROUP, "all-users")),
                false, createTemplate(), true,
                SubscriptionScope.SYSTEM
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
