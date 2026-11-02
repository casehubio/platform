package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryChannelDescriptor;
import io.casehub.platform.api.delivery.DeliveryChannels;
import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.api.delivery.EngagementRecorded;
import io.casehub.platform.api.delivery.EngagementType;
import io.casehub.platform.api.notification.NotificationClicked;
import io.casehub.platform.api.notification.NotificationReplied;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.api.notification.NotificationStatusChanged;
import io.casehub.platform.api.preferences.BooleanPreference;
import io.casehub.platform.api.preferences.PlatformPreferenceKeys;
import io.casehub.platform.api.subscription.NotificationTarget;
import io.casehub.platform.api.subscription.NotificationTemplate;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionMatched;
import io.casehub.platform.api.subscription.SubscriptionScope;
import io.casehub.platform.api.subscription.TargetType;
import io.casehub.platform.delivery.channel.inmem.InMemoryDeliveryChannelRegistry;
import io.casehub.platform.delivery.tracking.inmem.InMemoryDeliveryAttemptStore;
import io.casehub.platform.notification.inmem.InMemoryNotificationStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EngagementIntegrationTest {

    private InMemoryNotificationStore notificationStore;
    private InMemoryDeliveryAttemptStore deliveryAttemptStore;
    private InAppEngagementBridge bridge;
    private NotificationDispatcher dispatcher;
    private List<EngagementRecorded> engagementEvents;

    @BeforeEach
    void setUp() {
        notificationStore = new InMemoryNotificationStore(1000);
        deliveryAttemptStore = new InMemoryDeliveryAttemptStore(10000);
        engagementEvents = new ArrayList<>();

        var enabledProvider = EngagementRecorderTest.providerWith(
                PlatformPreferenceKeys.ENGAGEMENT_ENABLED, BooleanPreference.of(true));
        var recorder = new EngagementRecorder(deliveryAttemptStore, engagementEvents::add, enabledProvider);
        bridge = new InAppEngagementBridge(deliveryAttemptStore, recorder, enabledProvider);

        var channelRegistry = new InMemoryDeliveryChannelRegistry();
        var inAppDeliverer = new InAppNotificationDeliverer(notificationStore, channelRegistry);

        var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.findAndRegisterModules();
        var deliveryTracker = new DeliveryTracker(deliveryAttemptStore, objectMapper, Duration.ofSeconds(30));

        var targetResolver = new TargetResolver(
                (g, t) -> Set.of(), (et, eid, tid) -> Set.of());

        dispatcher = new NotificationDispatcher(
                targetResolver, new SuppressionEvaluator(), new ChannelRouter(channelRegistry),
                new StubPreferenceStore(), new StubSuppressionStore(),
                new StubDigestBuffer(), deliveryTracker,
                input -> input.replyHint());
    }

    @Test
    void fullChain_dispatch_to_opened_engagement() {
        var template = new NotificationTemplate(
                "Item {status}", null, NotificationSeverity.INFO, "work-item.updated",
                null, "work-item", "entityId", "actorId", null);
        var sub = new Subscription("sub-1", "owner-1", "tenant-1", "Test", "test.event",
                List.of(), List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false, template, true, SubscriptionScope.USER, Instant.now(), Instant.now());
        var pojo = new TestEvent("wi-1", "actor-ext", "completed");

        // Step 1: Dispatch — creates notification + delivery attempt with sourceId
        dispatcher.onMatch(new SubscriptionMatched(sub, pojo));

        // Step 2: Verify notification was stored
        var page = notificationStore.find(new io.casehub.platform.api.notification.NotificationQuery(
                "user-1", "tenant-1", null, null, null, 10));
        assertThat(page.notifications()).hasSize(1);
        var notification = page.notifications().getFirst();

        // Step 3: Verify delivery attempt has correct sourceId
        var attempts = deliveryAttemptStore.findBySource(
                notification.id(), DeliverySourceType.NOTIFICATION, "tenant-1");
        assertThat(attempts).hasSize(1);
        assertThat(attempts.getFirst().channelId()).isEqualTo(DeliveryChannels.IN_APP);

        // Step 4: Simulate markRead → NotificationStatusChanged → bridge records OPENED
        var readNotification = notificationStore.markRead(notification.id(), "user-1", "tenant-1").orElseThrow();
        bridge.onStatusChanged(new NotificationStatusChanged(readNotification, NotificationStatus.UNREAD));

        // Step 5: Verify OPENED engagement event was recorded
        var engagements = deliveryAttemptStore.findEngagementsByAttemptId(attempts.getFirst().id(), "tenant-1");
        assertThat(engagements).hasSize(1);
        assertThat(engagements.getFirst().type()).isEqualTo(EngagementType.OPENED);
        assertThat(engagementEvents).hasSize(1);
    }

    @Test
    void fullChain_dispatch_to_clicked_engagement() {
        var template = new NotificationTemplate(
                "Item {status}", null, NotificationSeverity.INFO, "work-item.updated",
                "/items/{entityId}", "work-item", "entityId", "actorId", null);
        var sub = new Subscription("sub-1", "owner-1", "tenant-1", "Test", "test.event",
                List.of(), List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false, template, true, SubscriptionScope.USER, Instant.now(), Instant.now());

        dispatcher.onMatch(new SubscriptionMatched(sub, new TestEvent("wi-1", "actor-ext", "done")));

        var notification = notificationStore.find(new io.casehub.platform.api.notification.NotificationQuery(
                "user-1", "tenant-1", null, null, null, 10)).notifications().getFirst();

        // Simulate click → bridge records CLICKED
        bridge.onClicked(new NotificationClicked(notification));

        var attempts = deliveryAttemptStore.findBySource(
                notification.id(), DeliverySourceType.NOTIFICATION, "tenant-1");
        var engagements = deliveryAttemptStore.findEngagementsByAttemptId(attempts.getFirst().id(), "tenant-1");
        assertThat(engagements).hasSize(1);
        assertThat(engagements.getFirst().type()).isEqualTo(EngagementType.CLICKED);
    }

    @Test
    void fullChain_dispatch_to_replied_engagement() {
        var hint = io.casehub.platform.api.notification.ReplyHint.options(List.of("approve", "reject"));
        var template = new NotificationTemplate(
                "Approve? {status}", null, NotificationSeverity.INFO, "approval.requested",
                null, "work-item", "entityId", "actorId", hint);
        var sub = new Subscription("sub-1", "owner-1", "tenant-1", "Test", "test.event",
                List.of(), List.of(new NotificationTarget(TargetType.USER, "user-1")),
                false, template, true, SubscriptionScope.USER, Instant.now(), Instant.now());

        dispatcher.onMatch(new SubscriptionMatched(sub, new TestEvent("wi-1", "actor-ext", "pending")));

        var notification = notificationStore.find(new io.casehub.platform.api.notification.NotificationQuery(
                "user-1", "tenant-1", null, null, null, 10)).notifications().getFirst();

        // Verify replyHint survived the dispatch chain
        assertThat(notification.replyHint()).isNotNull();
        assertThat(notification.replyHint().options()).containsExactly("approve", "reject");

        // Simulate reply → bridge records REPLIED with value
        bridge.onReplied(new NotificationReplied(notification, "approve"));

        var attempts = deliveryAttemptStore.findBySource(
                notification.id(), DeliverySourceType.NOTIFICATION, "tenant-1");
        var engagements = deliveryAttemptStore.findEngagementsByAttemptId(attempts.getFirst().id(), "tenant-1");
        assertThat(engagements).hasSize(1);
        assertThat(engagements.getFirst().type()).isEqualTo(EngagementType.REPLIED);
        assertThat(engagements.getFirst().metadata()).isEqualTo("approve");
    }

    record TestEvent(String entityId, String actorId, String status) {}

    private static class StubPreferenceStore implements io.casehub.platform.api.notification.settings.NotificationPreferenceStore {
        @Override public Optional<io.casehub.platform.api.notification.settings.NotificationPreferences> get(String u, String t) { return Optional.empty(); }
        @Override public io.casehub.platform.api.notification.settings.NotificationPreferences update(String u, String t, io.casehub.platform.api.notification.settings.NotificationPreferenceUpdate upd) { throw new UnsupportedOperationException(); }
    }

    private static class StubSuppressionStore implements io.casehub.platform.api.notification.settings.SuppressionStore {
        @Override public io.casehub.platform.api.notification.settings.MuteRule addMute(io.casehub.platform.api.notification.settings.MuteRuleInput i) { throw new UnsupportedOperationException(); }
        @Override public List<io.casehub.platform.api.notification.settings.MuteRule> activeMutes(String u, String t) { return List.of(); }
        @Override public boolean removeMute(String id, String u, String t) { throw new UnsupportedOperationException(); }
        @Override public io.casehub.platform.api.notification.settings.Snooze activateSnooze(io.casehub.platform.api.notification.settings.SnoozeInput i) { throw new UnsupportedOperationException(); }
        @Override public Optional<io.casehub.platform.api.notification.settings.Snooze> activeSnooze(String u, String t) { return Optional.empty(); }
        @Override public boolean cancelSnooze(String u, String t) { throw new UnsupportedOperationException(); }
    }

    private static class StubDigestBuffer implements io.casehub.platform.api.delivery.DigestBuffer {
        @Override public void add(io.casehub.platform.api.delivery.DigestBufferKey k, io.casehub.platform.api.notification.NotificationInput n) {}
        @Override public List<io.casehub.platform.api.notification.NotificationInput> drain(io.casehub.platform.api.delivery.DigestBufferKey k) { return List.of(); }
        @Override public Set<io.casehub.platform.api.delivery.DigestBufferKey> pendingKeys() { return Set.of(); }
        @Override public Optional<Instant> oldestPendingTimestamp(io.casehub.platform.api.delivery.DigestBufferKey k) { return Optional.empty(); }
        @Override public int pendingCount(io.casehub.platform.api.delivery.DigestBufferKey k) { return 0; }
        @Override public Set<io.casehub.platform.api.delivery.DigestBufferKey> pendingKeysForUser(String u, String t) { return Set.of(); }
    }
}
