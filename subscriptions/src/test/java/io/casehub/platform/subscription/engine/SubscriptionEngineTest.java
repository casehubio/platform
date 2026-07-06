package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStore;
import io.casehub.platform.api.notification.UUIDv7;
import io.casehub.platform.api.subscription.Constraint;
import io.casehub.platform.api.subscription.ConstraintOp;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionCreated;
import io.casehub.platform.api.subscription.SubscriptionDeleted;
import io.casehub.platform.api.subscription.SubscriptionInput;
import io.casehub.platform.api.subscription.SubscriptionUpdated;
import io.casehub.platform.api.subscription.TargetType;
import io.casehub.platform.datasource.memory.InMemoryDataSourceRegistry;
import io.casehub.platform.subscription.inmem.InMemorySubscriptionStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionEngineTest {

    private InMemoryDataSourceRegistry registry;
    private InMemorySubscriptionStore subStore;
    private CapturingNotificationStore notifStore;
    private SubscriptionEngine engine;

    @BeforeEach
    void setUp() {
        registry = new InMemoryDataSourceRegistry(null);
        subStore = new InMemorySubscriptionStore(null, null, null);
        notifStore = new CapturingNotificationStore();
        engine = new SubscriptionEngine(registry, subStore, notifStore);
        UUIDv7.resetState();
    }

    // --- Startup ---

    @Test
    void startup_registersNotificationDataSource() {
        engine.onStartup(null);

        var ds = registry.resolveSource(NOTIFICATION_DATASOURCE_PATH, PLATFORM_TENANT_ID);
        assertThat(ds).isPresent();
    }

    @Test
    void startup_wiresExistingEnabledSubscriptions() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));
        subStore.store(subscriptionInput("user-2", "tenant-1",
                "io.casehub.work.workitem.completed", true));

        engine.onStartup(null);

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).hasSize(2);
    }

    @Test
    void startup_doesNotWireDisabledSubscriptions() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", false));

        engine.onStartup(null);

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    // --- Event Matching ---

    @Test
    void event_matchesSubscription_createsNotification() {
        var template = new NotificationTemplate("WorkItem {status}", null,
                NotificationSeverity.INFO, "work-item.completed",
                null, "work-item", "workItemId", "actor");
        var input = new SubscriptionInput("user-1", "tenant-1", "My sub",
                "io.casehub.work.workitem.completed",
                List.of(), List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false, template, true);
        subStore.store(input);

        engine.onStartup(null);

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "user-2");

        assertThat(notifStore.captured()).hasSize(1);
        var notification = notifStore.captured().get(0);
        assertThat(notification.title()).isEqualTo("WorkItem completed");
        assertThat(notification.userId()).isEqualTo("user-1");
        assertThat(notification.tenancyId()).isEqualTo("tenant-1");
        assertThat(notification.category()).isEqualTo("work-item.completed");
    }

    @Test
    void event_wrongEventType_doesNotMatch() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));

        engine.onStartup(null);

        pushEvent("io.casehub.work.workitem.created", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    // --- Tenant Isolation ---

    @Test
    void event_wrongTenant_doesNotMatch() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));

        engine.onStartup(null);

        pushEvent("io.casehub.work.workitem.completed", "tenant-2",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    @Test
    void event_matchesSameTenantOnly() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));
        subStore.store(subscriptionInput("user-2", "tenant-2",
                "io.casehub.work.workitem.completed", true));

        engine.onStartup(null);

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).hasSize(1);
        assertThat(notifStore.captured().get(0).userId()).isEqualTo("user-1");
        assertThat(notifStore.captured().get(0).tenancyId()).isEqualTo("tenant-1");
    }

    // --- Dynamic Wiring: SubscriptionCreated ---

    @Test
    void onCreated_wiresEnabledSubscription() {
        engine.onStartup(null);

        var sub = subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));
        engine.onCreated(new SubscriptionCreated(sub));

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).hasSize(1);
    }

    @Test
    void onCreated_doesNotWireDisabledSubscription() {
        engine.onStartup(null);

        var sub = subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", false));
        engine.onCreated(new SubscriptionCreated(sub));

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    // --- Dynamic Wiring: SubscriptionUpdated ---

    @Test
    void onUpdated_rewiresSubscription() {
        // Start with a subscription matching event type A
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));
        engine.onStartup(null);

        // Update to match event type B
        var allEnabled = subStore.findAllEnabled().toList();
        var original = allEnabled.get(0);
        var updated = new Subscription(
                original.id(), original.ownerId(), original.tenancyId(), original.name(),
                "io.casehub.work.workitem.created",
                original.constraints(), original.targets(), original.includeActor(),
                original.template(), true,
                original.createdAt(), Instant.now());

        engine.onUpdated(new SubscriptionUpdated(updated, original));

        // Old type should no longer match
        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");
        assertThat(notifStore.captured()).isEmpty();

        // New type should match
        pushEvent("io.casehub.work.workitem.created", "tenant-1",
                UUID.randomUUID(), "actor-1");
        assertThat(notifStore.captured()).hasSize(1);
    }

    @Test
    void onUpdated_disablingUnwiresSubscription() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));
        engine.onStartup(null);

        var original = subStore.findAllEnabled().toList().get(0);
        var disabled = new Subscription(
                original.id(), original.ownerId(), original.tenancyId(), original.name(),
                original.eventType(), original.constraints(), original.targets(),
                original.includeActor(), original.template(), false,
                original.createdAt(), Instant.now());

        engine.onUpdated(new SubscriptionUpdated(disabled, original));

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    // --- Dynamic Wiring: SubscriptionDeleted ---

    @Test
    void onDeleted_unwiresSubscription() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));
        engine.onStartup(null);

        // Verify it was wired
        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");
        assertThat(notifStore.captured()).hasSize(1);

        // Delete and push again
        notifStore.clear();
        var sub = subStore.findAllEnabled().toList().get(0);
        engine.onDeleted(new SubscriptionDeleted(sub));

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    @Test
    void onDeleted_noOpWhenSubscriptionNotWired() {
        engine.onStartup(null);

        // Delete a subscription that was never wired — should not throw
        var ghost = new Subscription("ghost-id", "user-1", "tenant-1", "Ghost",
                "io.casehub.work.ghost", List.of(),
                List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false, defaultTemplate(), true,
                Instant.now(), Instant.now());
        engine.onDeleted(new SubscriptionDeleted(ghost));

        // Engine still operational
        pushEvent("io.casehub.work.ghost", "tenant-1", UUID.randomUUID(), "actor-1");
        assertThat(notifStore.captured()).isEmpty();
    }

    // --- Ghost subscription prevention (R2-03) ---

    @Test
    void concurrentUpdates_noGhostSubscriptions() {
        subStore.store(subscriptionInput("user-1", "tenant-1",
                "io.casehub.work.workitem.completed", true));
        engine.onStartup(null);

        var original = subStore.findAllEnabled().toList().get(0);

        // Simulate rapid update + delete — delete must win
        var updated = new Subscription(
                original.id(), original.ownerId(), original.tenancyId(), original.name(),
                "io.casehub.work.workitem.created",
                original.constraints(), original.targets(), original.includeActor(),
                original.template(), true,
                original.createdAt(), Instant.now());

        engine.onUpdated(new SubscriptionUpdated(updated, original));
        engine.onDeleted(new SubscriptionDeleted(updated));

        // Neither old nor new type should match
        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");
        pushEvent("io.casehub.work.workitem.created", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    // --- Template resolution failure ---

    @Test
    void event_templateResolutionReturnsNull_noNotificationStored() {
        // Template with unresolvable entityIdField
        var template = new NotificationTemplate("Title", null,
                NotificationSeverity.INFO, "test", null,
                "entity", "missingField", "actor");
        var input = new SubscriptionInput("user-1", "tenant-1", "Sub",
                "io.casehub.work.workitem.completed",
                List.of(), List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false, template, true);
        subStore.store(input);

        engine.onStartup(null);

        pushEvent("io.casehub.work.workitem.completed", "tenant-1",
                UUID.randomUUID(), "actor-1");

        assertThat(notifStore.captured()).isEmpty();
    }

    // --- Helpers ---

    private SubscriptionInput subscriptionInput(final String ownerId, final String tenancyId,
                                                final String eventType, final boolean enabled) {
        return new SubscriptionInput(ownerId, tenancyId, "Test sub",
                eventType, List.of(),
                List.of(new NotificationTarget(TargetType.USER, ownerId)),
                false, defaultTemplate(), enabled);
    }

    private NotificationTemplate defaultTemplate() {
        return new NotificationTemplate("WorkItem {status}", null,
                NotificationSeverity.INFO, "work-item.completed",
                null, "work-item", "workItemId", "actor");
    }

    @SuppressWarnings("unchecked")
    private void pushEvent(final String type, final String tenancyId,
                           final UUID workItemId, final String actor) {
        var ds = registry.resolveSource(NOTIFICATION_DATASOURCE_PATH, PLATFORM_TENANT_ID)
                .orElseThrow();
        ((DataSource<Object>) ds).add(
                new TestEvent(type, tenancyId, workItemId, actor, "completed"));
    }

    // --- Test doubles ---

    record TestEvent(String type, String tenancyId, UUID workItemId,
                     String actor, String status) {}

    /**
     * Test double that captures all stored notifications for assertion.
     * All query methods return empty — only store() is meaningful.
     */
    static final class CapturingNotificationStore implements NotificationStore {

        private final List<NotificationInput> captured = Collections.synchronizedList(new ArrayList<>());

        @Override
        public Notification store(final NotificationInput input) {
            captured.add(input);
            return new Notification(
                    UUIDv7.generate(),
                    input.userId(),
                    input.tenancyId(),
                    input.title(),
                    input.body(),
                    input.category(),
                    input.severity(),
                    input.actionUrl(),
                    input.source(),
                    NotificationStatus.UNREAD,
                    Instant.now(),
                    null,
                    null);
        }

        @Override
        public List<Notification> storeAll(final List<NotificationInput> inputs) {
            return inputs.stream().map(this::store).toList();
        }

        @Override
        public NotificationPage find(final NotificationQuery query) {
            return new NotificationPage(List.of(), null);
        }

        @Override
        public long unreadCount(final String userId, final String tenancyId) {
            return 0;
        }

        @Override
        public Optional<Notification> markRead(final String id, final String userId,
                                                final String tenancyId) {
            return Optional.empty();
        }

        @Override
        public Optional<Notification> dismiss(final String id, final String userId,
                                               final String tenancyId) {
            return Optional.empty();
        }

        @Override
        public int markAllRead(final String userId, final String tenancyId) {
            return 0;
        }

        List<NotificationInput> captured() {
            return List.copyOf(captured);
        }

        void clear() {
            captured.clear();
        }
    }
}
