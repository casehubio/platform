package io.casehub.platform.api.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test base for {@link NotificationStore} implementations. Concrete subclasses
 * provide the store instance and state clearing logic.
 *
 * <p>Covers: store + retrieve, storeAll, find with filters, unreadCount, markRead,
 * dismiss, markAllRead, status lifecycle (UNREAD→READ→DISMISSED, UNREAD→DISMISSED),
 * tenant isolation, user ownership enforcement, cursor pagination ordering.
 */
public abstract class NotificationStoreContractTest {

    protected abstract NotificationStore store();

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
    void store_persistsNotificationWithGeneratedId() {
        var input = createInput("user-1", "tenant-1", "Title", "work-item.created");
        var notification = store().store(input);

        assertThat(notification.id()).isNotNull();
        assertThat(notification.userId()).isEqualTo("user-1");
        assertThat(notification.tenancyId()).isEqualTo("tenant-1");
        assertThat(notification.title()).isEqualTo("Title");
        assertThat(notification.category()).isEqualTo("work-item.created");
        assertThat(notification.status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(notification.createdAt()).isNotNull();
        assertThat(notification.readAt()).isNull();
        assertThat(notification.dismissedAt()).isNull();
    }

    @Test
    void store_setsCreatedAtToApproximatelyNow() {
        var before = Instant.now();
        var notification = store().store(createInput("user-1", "tenant-1", "Title", "category"));
        var after = Instant.now();

        assertThat(notification.createdAt()).isBetween(before, after);
    }

    @Test
    void storeAll_persistsMultipleNotifications() {
        var input1 = createInput("user-1", "tenant-1", "Title 1", "category");
        var input2 = createInput("user-2", "tenant-1", "Title 2", "category");

        var notifications = store().storeAll(List.of(input1, input2));

        assertThat(notifications).hasSize(2);
        assertThat(notifications.get(0).title()).isEqualTo("Title 1");
        assertThat(notifications.get(1).title()).isEqualTo("Title 2");
    }

    // Query Tests

    @Test
    void find_returnsNotificationsForUser() {
        store().store(createInput("user-1", "tenant-1", "Title 1", "category"));
        store().store(createInput("user-1", "tenant-1", "Title 2", "category"));
        store().store(createInput("user-2", "tenant-1", "Title 3", "category"));

        var query = new NotificationQuery("user-1", "tenant-1", null, null, null, 10);
        var page = store().find(query);

        assertThat(page.notifications()).hasSize(2);
        assertThat(page.notifications()).allMatch(n -> n.userId().equals("user-1"));
    }

    @Test
    void find_filtersByStatus() {
        var notif1 = store().store(createInput("user-1", "tenant-1", "Unread", "category"));
        store().markRead(notif1.id(), "user-1", "tenant-1");
        store().store(createInput("user-1", "tenant-1", "Another Unread", "category"));

        var query = new NotificationQuery("user-1", "tenant-1", NotificationStatus.UNREAD, null, null, 10);
        var page = store().find(query);

        assertThat(page.notifications()).hasSize(1);
        assertThat(page.notifications().get(0).title()).isEqualTo("Another Unread");
        assertThat(page.notifications().get(0).status()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    void find_filtersByCategory() {
        store().store(createInput("user-1", "tenant-1", "Work Item", "work-item.created"));
        store().store(createInput("user-1", "tenant-1", "SLA", "sla.breached"));

        var query = new NotificationQuery("user-1", "tenant-1", null, "sla.breached", null, 10);
        var page = store().find(query);

        assertThat(page.notifications()).hasSize(1);
        assertThat(page.notifications().get(0).category()).isEqualTo("sla.breached");
    }

    @Test
    void find_ordersNewestFirst() {
        // UUIDv7 sequence counter ensures deterministic ordering within same millisecond
        var notif1 = store().store(createInput("user-1", "tenant-1", "First", "category"));
        var notif2 = store().store(createInput("user-1", "tenant-1", "Second", "category"));

        var query = new NotificationQuery("user-1", "tenant-1", null, null, null, 10);
        var page = store().find(query);

        assertThat(page.notifications()).hasSize(2);
        assertThat(page.notifications().get(0).title()).isEqualTo("Second");
        assertThat(page.notifications().get(1).title()).isEqualTo("First");
    }

    @Test
    void find_paginatesWithCursor() {
        for (int i = 0; i < 5; i++) {
            store().store(createInput("user-1", "tenant-1", "Title " + i, "category"));
        }

        var query1 = new NotificationQuery("user-1", "tenant-1", null, null, null, 2);
        var page1 = store().find(query1);

        assertThat(page1.notifications()).hasSize(2);
        assertThat(page1.nextCursor()).isNotNull();

        var query2 = new NotificationQuery("user-1", "tenant-1", null, null, page1.nextCursor(), 2);
        var page2 = store().find(query2);

        assertThat(page2.notifications()).hasSize(2);
        // Ensure no overlap
        assertThat(page2.notifications()).noneMatch(n -> page1.notifications().contains(n));
    }

    @Test
    void find_lastPageHasNullCursor() {
        store().store(createInput("user-1", "tenant-1", "Only One", "category"));

        var query = new NotificationQuery("user-1", "tenant-1", null, null, null, 10);
        var page = store().find(query);

        assertThat(page.notifications()).hasSize(1);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void find_respectsTenantIsolation() {
        store().store(createInput("user-1", "tenant-1", "Tenant 1", "category"));
        store().store(createInput("user-1", "tenant-2", "Tenant 2", "category"));

        var query = new NotificationQuery("user-1", "tenant-1", null, null, null, 10);
        var page = store().find(query);

        assertThat(page.notifications()).hasSize(1);
        assertThat(page.notifications().get(0).tenancyId()).isEqualTo("tenant-1");
    }

    // Unread Count Tests

    @Test
    void unreadCount_countsOnlyUnreadNotifications() {
        var notif1 = store().store(createInput("user-1", "tenant-1", "Unread 1", "category"));
        store().store(createInput("user-1", "tenant-1", "Unread 2", "category"));
        store().markRead(notif1.id(), "user-1", "tenant-1");

        var count = store().unreadCount("user-1", "tenant-1");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void unreadCount_respectsTenantIsolation() {
        store().store(createInput("user-1", "tenant-1", "Unread", "category"));
        store().store(createInput("user-1", "tenant-2", "Unread", "category"));

        var count = store().unreadCount("user-1", "tenant-1");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void unreadCount_respectsUserIsolation() {
        store().store(createInput("user-1", "tenant-1", "Unread", "category"));
        store().store(createInput("user-2", "tenant-1", "Unread", "category"));

        var count = store().unreadCount("user-1", "tenant-1");

        assertThat(count).isEqualTo(1);
    }

    // Mark Read Tests

    @Test
    void markRead_transitionsUnreadToRead() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var updated = store().markRead(notif.id(), "user-1", "tenant-1");

        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(NotificationStatus.READ);
        assertThat(updated.get().readAt()).isNotNull();
    }

    @Test
    void markRead_setsReadAtToApproximatelyNow() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var before = Instant.now();
        var updated = store().markRead(notif.id(), "user-1", "tenant-1");
        var after = Instant.now();

        assertThat(updated).isPresent();
        assertThat(updated.get().readAt()).isBetween(before, after);
    }

    @Test
    void markRead_wrongUser_returnsEmpty() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var updated = store().markRead(notif.id(), "user-2", "tenant-1");

        assertThat(updated).isEmpty();
    }

    @Test
    void markRead_wrongTenant_returnsEmpty() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var updated = store().markRead(notif.id(), "user-1", "tenant-2");

        assertThat(updated).isEmpty();
    }

    @Test
    void markRead_nonExistent_returnsEmpty() {
        var updated = store().markRead("non-existent", "user-1", "tenant-1");

        assertThat(updated).isEmpty();
    }

    // Dismiss Tests

    @Test
    void dismiss_transitionsUnreadToDismissed() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var updated = store().dismiss(notif.id(), "user-1", "tenant-1");

        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(NotificationStatus.DISMISSED);
        assertThat(updated.get().dismissedAt()).isNotNull();
    }

    @Test
    void dismiss_transitionsReadToDismissed() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));
        store().markRead(notif.id(), "user-1", "tenant-1");

        var updated = store().dismiss(notif.id(), "user-1", "tenant-1");

        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo(NotificationStatus.DISMISSED);
        assertThat(updated.get().dismissedAt()).isNotNull();
    }

    @Test
    void dismiss_setsDismissedAtToApproximatelyNow() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var before = Instant.now();
        var updated = store().dismiss(notif.id(), "user-1", "tenant-1");
        var after = Instant.now();

        assertThat(updated).isPresent();
        assertThat(updated.get().dismissedAt()).isBetween(before, after);
    }

    @Test
    void dismiss_wrongUser_returnsEmpty() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var updated = store().dismiss(notif.id(), "user-2", "tenant-1");

        assertThat(updated).isEmpty();
    }

    @Test
    void dismiss_wrongTenant_returnsEmpty() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));

        var updated = store().dismiss(notif.id(), "user-1", "tenant-2");

        assertThat(updated).isEmpty();
    }

    @Test
    void dismiss_nonExistent_returnsEmpty() {
        var updated = store().dismiss("non-existent", "user-1", "tenant-1");

        assertThat(updated).isEmpty();
    }

    // Mark All Read Tests

    @Test
    void markAllRead_marksAllUnreadAsRead() {
        store().store(createInput("user-1", "tenant-1", "Unread 1", "category"));
        store().store(createInput("user-1", "tenant-1", "Unread 2", "category"));

        var count = store().markAllRead("user-1", "tenant-1");

        assertThat(count).isEqualTo(2);
        assertThat(store().unreadCount("user-1", "tenant-1")).isEqualTo(0);
    }

    @Test
    void markAllRead_doesNotAffectAlreadyRead() {
        var notif1 = store().store(createInput("user-1", "tenant-1", "Already Read", "category"));
        store().markRead(notif1.id(), "user-1", "tenant-1");
        store().store(createInput("user-1", "tenant-1", "Unread", "category"));

        var count = store().markAllRead("user-1", "tenant-1");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void markAllRead_doesNotAffectDismissed() {
        var notif1 = store().store(createInput("user-1", "tenant-1", "Dismissed", "category"));
        store().dismiss(notif1.id(), "user-1", "tenant-1");
        store().store(createInput("user-1", "tenant-1", "Unread", "category"));

        var count = store().markAllRead("user-1", "tenant-1");

        assertThat(count).isEqualTo(1);
    }

    @Test
    void markAllRead_respectsTenantIsolation() {
        store().store(createInput("user-1", "tenant-1", "Unread", "category"));
        store().store(createInput("user-1", "tenant-2", "Unread", "category"));

        var count = store().markAllRead("user-1", "tenant-1");

        assertThat(count).isEqualTo(1);
        assertThat(store().unreadCount("user-1", "tenant-2")).isEqualTo(1);
    }

    @Test
    void markAllRead_respectsUserIsolation() {
        store().store(createInput("user-1", "tenant-1", "Unread", "category"));
        store().store(createInput("user-2", "tenant-1", "Unread", "category"));

        var count = store().markAllRead("user-1", "tenant-1");

        assertThat(count).isEqualTo(1);
        assertThat(store().unreadCount("user-2", "tenant-1")).isEqualTo(1);
    }

    @Test
    void markAllRead_noUnreadNotifications_returnsZero() {
        var count = store().markAllRead("user-1", "tenant-1");

        assertThat(count).isEqualTo(0);
    }

    // DISMISSED Terminal State Tests

    @Test
    void markRead_dismissedNotification_returnsEmpty() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));
        store().dismiss(notif.id(), "user-1", "tenant-1");

        var updated = store().markRead(notif.id(), "user-1", "tenant-1");

        assertThat(updated).isEmpty();
    }

    @Test
    void dismiss_alreadyDismissedNotification_returnsEmpty() {
        var notif = store().store(createInput("user-1", "tenant-1", "Title", "category"));
        store().dismiss(notif.id(), "user-1", "tenant-1");

        var updated = store().dismiss(notif.id(), "user-1", "tenant-1");

        assertThat(updated).isEmpty();
    }

    // Helper Methods

    private NotificationInput createInput(String userId, String tenancyId, String title, String category) {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        return new NotificationInput(
                userId,
                tenancyId,
                title,
                "Body for " + title,
                category,
                NotificationSeverity.INFO,
                "/action",
                source
        );
    }
}
