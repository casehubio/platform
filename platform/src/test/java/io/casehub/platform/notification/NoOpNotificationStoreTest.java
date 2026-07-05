package io.casehub.platform.notification;

import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.NotificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NoOpNotificationStore} — verifies no-op contract: store returns
 * structurally valid records, queries return empty, mutations return empty/zero,
 * no exceptions thrown.
 */
class NoOpNotificationStoreTest {

    private NoOpNotificationStore store;

    @BeforeEach
    void setUp() {
        store = new NoOpNotificationStore();
    }

    @Test
    void store_shouldReturnValidNotification() {
        NotificationInput input = new NotificationInput(
                "user123",
                "tenant456",
                "Test Notification",
                "Body content",
                "test.category",
                NotificationSeverity.INFO,
                "/actions/test",
                new NotificationSource("event1", "work-item", "wi123", "actor1")
        );

        Notification result = store.store(input);

        assertNotNull(result);
        assertNotNull(result.id(), "id should be generated");
        assertEquals("user123", result.userId());
        assertEquals("tenant456", result.tenancyId());
        assertEquals("Test Notification", result.title());
        assertEquals("Body content", result.body());
        assertEquals("test.category", result.category());
        assertEquals(NotificationSeverity.INFO, result.severity());
        assertEquals("/actions/test", result.actionUrl());
        assertEquals("event1", result.source().eventId());
        assertEquals(NotificationStatus.UNREAD, result.status());
        assertNotNull(result.createdAt(), "createdAt should be set");
        assertNull(result.readAt(), "readAt should be null for new notification");
        assertNull(result.dismissedAt(), "dismissedAt should be null for new notification");
    }

    @Test
    void store_shouldHandleNullableFields() {
        NotificationInput input = new NotificationInput(
                "user123",
                "tenant456",
                "Title Only",
                null,  // null body
                "test.category",
                NotificationSeverity.WARNING,
                null,  // null actionUrl
                new NotificationSource("event1", "case", "case123", "actor1")
        );

        Notification result = store.store(input);

        assertNotNull(result);
        assertNull(result.body());
        assertNull(result.actionUrl());
    }

    @Test
    void storeAll_shouldReturnValidNotifications() {
        NotificationInput input1 = new NotificationInput(
                "user1",
                "tenant1",
                "Notification 1",
                "Body 1",
                "category.1",
                NotificationSeverity.INFO,
                null,
                new NotificationSource("event1", "type1", "entity1", "actor1")
        );
        NotificationInput input2 = new NotificationInput(
                "user2",
                "tenant2",
                "Notification 2",
                null,
                "category.2",
                NotificationSeverity.URGENT,
                "/action2",
                new NotificationSource("event2", "type2", "entity2", "actor2")
        );

        List<Notification> results = store.storeAll(List.of(input1, input2));

        assertNotNull(results);
        assertEquals(2, results.size());

        Notification result1 = results.get(0);
        assertNotNull(result1.id());
        assertEquals("user1", result1.userId());
        assertEquals(NotificationStatus.UNREAD, result1.status());

        Notification result2 = results.get(1);
        assertNotNull(result2.id());
        assertEquals("user2", result2.userId());
        assertEquals(NotificationStatus.UNREAD, result2.status());
    }

    @Test
    void storeAll_shouldHandleEmptyList() {
        List<Notification> results = store.storeAll(List.of());

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void find_shouldReturnEmptyPage() {
        NotificationQuery query = new NotificationQuery(
                "user123",
                "tenant456",
                null,  // all statuses
                null,  // all categories
                null,  // no cursor
                10
        );

        NotificationPage page = store.find(query);

        assertNotNull(page);
        assertTrue(page.notifications().isEmpty(), "no-op should return empty list");
        assertNull(page.nextCursor(), "no-op should have no next cursor");
    }

    @Test
    void find_shouldReturnEmptyPage_withFilters() {
        NotificationQuery query = new NotificationQuery(
                "user123",
                "tenant456",
                NotificationStatus.UNREAD,
                "specific.category",
                "some-cursor",
                5
        );

        NotificationPage page = store.find(query);

        assertNotNull(page);
        assertTrue(page.notifications().isEmpty());
        assertNull(page.nextCursor());
    }

    @Test
    void unreadCount_shouldReturnZero() {
        long count = store.unreadCount("user123", "tenant456");

        assertEquals(0L, count, "no-op should return zero count");
    }

    @Test
    void markRead_shouldReturnEmpty() {
        Optional<Notification> result = store.markRead("notification123", "user123", "tenant456");

        assertNotNull(result);
        assertTrue(result.isEmpty(), "no-op should return empty");
    }

    @Test
    void dismiss_shouldReturnEmpty() {
        Optional<Notification> result = store.dismiss("notification123", "user123", "tenant456");

        assertNotNull(result);
        assertTrue(result.isEmpty(), "no-op should return empty");
    }

    @Test
    void markAllRead_shouldReturnZero() {
        int count = store.markAllRead("user123", "tenant456");

        assertEquals(0, count, "no-op should return zero count");
    }

    @Test
    void store_shouldGenerateUniqueIds() {
        NotificationInput input = new NotificationInput(
                "user123",
                "tenant456",
                "Test",
                null,
                "test.category",
                NotificationSeverity.INFO,
                null,
                new NotificationSource("event1", "type1", "entity1", "actor1")
        );

        Notification result1 = store.store(input);
        Notification result2 = store.store(input);

        assertNotEquals(result1.id(), result2.id(), "each store() should generate unique id");
    }
}
