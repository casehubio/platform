package io.casehub.platform.api.notification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validates SPI record construction, null validation, defensive copies.
 */
class NotificationSpiTest {

    // NotificationSource tests

    @Test
    void notificationSource_validConstruction() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThat(source.eventId()).isEqualTo("evt-123");
        assertThat(source.entityType()).isEqualTo("work-item");
        assertThat(source.entityId()).isEqualTo("wi-456");
        assertThat(source.actorId()).isEqualTo("actor-789");
    }

    @Test
    void notificationSource_rejectsNullEventId() {
        assertThatThrownBy(() -> new NotificationSource(null, "work-item", "wi-456", "actor-789"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void notificationSource_rejectsNullEntityType() {
        assertThatThrownBy(() -> new NotificationSource("evt-123", null, "wi-456", "actor-789"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entityType");
    }

    @Test
    void notificationSource_rejectsNullEntityId() {
        assertThatThrownBy(() -> new NotificationSource("evt-123", "work-item", null, "actor-789"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entityId");
    }

    @Test
    void notificationSource_rejectsNullActorId() {
        assertThatThrownBy(() -> new NotificationSource("evt-123", "work-item", "wi-456", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("actorId");
    }

    // NotificationInput tests

    @Test
    void notificationInput_validConstruction() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var input = new NotificationInput(
                "user-1",
                "tenant-1",
                "New work item assigned",
                "Work item WI-456 has been assigned to you",
                "work-item.assigned",
                NotificationSeverity.INFO,
                "/work-items/wi-456",
                source
        );
        assertThat(input.userId()).isEqualTo("user-1");
        assertThat(input.tenancyId()).isEqualTo("tenant-1");
        assertThat(input.title()).isEqualTo("New work item assigned");
        assertThat(input.body()).isEqualTo("Work item WI-456 has been assigned to you");
        assertThat(input.category()).isEqualTo("work-item.assigned");
        assertThat(input.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(input.actionUrl()).isEqualTo("/work-items/wi-456");
        assertThat(input.source()).isEqualTo(source);
    }

    @Test
    void notificationInput_acceptsNullBody() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var input = new NotificationInput(
                "user-1",
                "tenant-1",
                "New work item assigned",
                null,
                "work-item.assigned",
                NotificationSeverity.INFO,
                null,
                source
        );
        assertThat(input.body()).isNull();
        assertThat(input.actionUrl()).isNull();
    }

    @Test
    void notificationInput_rejectsNullUserId() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new NotificationInput(
                null,
                "tenant-1",
                "Title",
                "Body",
                "category",
                NotificationSeverity.INFO,
                null,
                source
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void notificationInput_rejectsNullTenancyId() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new NotificationInput(
                "user-1",
                null,
                "Title",
                "Body",
                "category",
                NotificationSeverity.INFO,
                null,
                source
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenancyId");
    }

    @Test
    void notificationInput_rejectsNullTitle() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new NotificationInput(
                "user-1",
                "tenant-1",
                null,
                "Body",
                "category",
                NotificationSeverity.INFO,
                null,
                source
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("title");
    }

    @Test
    void notificationInput_rejectsNullCategory() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new NotificationInput(
                "user-1",
                "tenant-1",
                "Title",
                "Body",
                null,
                NotificationSeverity.INFO,
                null,
                source
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("category");
    }

    @Test
    void notificationInput_rejectsNullSeverity() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new NotificationInput(
                "user-1",
                "tenant-1",
                "Title",
                "Body",
                "category",
                null,
                null,
                source
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void notificationInput_rejectsNullSource() {
        assertThatThrownBy(() -> new NotificationInput(
                "user-1",
                "tenant-1",
                "Title",
                "Body",
                "category",
                NotificationSeverity.INFO,
                null,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("source");
    }

    // Notification tests

    @Test
    void notification_validConstruction() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var createdAt = Instant.now();
        var readAt = createdAt.plusSeconds(60);
        var dismissedAt = createdAt.plusSeconds(120);

        var notification = new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "New work item assigned",
                "Work item WI-456 has been assigned to you",
                "work-item.assigned",
                NotificationSeverity.INFO,
                "/work-items/wi-456",
                source,
                NotificationStatus.READ,
                createdAt,
                readAt,
                dismissedAt
        );

        assertThat(notification.id()).isEqualTo("notif-123");
        assertThat(notification.userId()).isEqualTo("user-1");
        assertThat(notification.tenancyId()).isEqualTo("tenant-1");
        assertThat(notification.title()).isEqualTo("New work item assigned");
        assertThat(notification.body()).isEqualTo("Work item WI-456 has been assigned to you");
        assertThat(notification.category()).isEqualTo("work-item.assigned");
        assertThat(notification.severity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(notification.actionUrl()).isEqualTo("/work-items/wi-456");
        assertThat(notification.source()).isEqualTo(source);
        assertThat(notification.status()).isEqualTo(NotificationStatus.READ);
        assertThat(notification.createdAt()).isEqualTo(createdAt);
        assertThat(notification.readAt()).isEqualTo(readAt);
        assertThat(notification.dismissedAt()).isEqualTo(dismissedAt);
    }

    @Test
    void notification_acceptsNullableFields() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var notification = new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,  // body
                "category",
                NotificationSeverity.INFO,
                null,  // actionUrl
                source,
                NotificationStatus.UNREAD,
                Instant.now(),
                null,  // readAt
                null   // dismissedAt
        );
        assertThat(notification.body()).isNull();
        assertThat(notification.actionUrl()).isNull();
        assertThat(notification.readAt()).isNull();
        assertThat(notification.dismissedAt()).isNull();
    }

    @Test
    void notification_rejectsNullId() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new Notification(
                null,
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                NotificationStatus.UNREAD,
                Instant.now(),
                null,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    void notification_rejectsNullStatus() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                null,  // status
                Instant.now(),
                null,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("status");
    }

    @Test
    void notification_rejectsNullCreatedAt() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        assertThatThrownBy(() -> new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                NotificationStatus.UNREAD,
                null,  // createdAt
                null,
                null
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("createdAt");
    }

    // NotificationQuery tests

    @Test
    void notificationQuery_validConstruction() {
        var query = new NotificationQuery(
                "user-1",
                "tenant-1",
                NotificationStatus.UNREAD,
                "work-item.assigned",
                "cursor-abc",
                25
        );
        assertThat(query.userId()).isEqualTo("user-1");
        assertThat(query.tenancyId()).isEqualTo("tenant-1");
        assertThat(query.status()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(query.category()).isEqualTo("work-item.assigned");
        assertThat(query.cursor()).isEqualTo("cursor-abc");
        assertThat(query.limit()).isEqualTo(25);
    }

    @Test
    void notificationQuery_acceptsNullStatusAndCategory() {
        var query = new NotificationQuery(
                "user-1",
                "tenant-1",
                null,
                null,
                null,
                10
        );
        assertThat(query.status()).isNull();
        assertThat(query.category()).isNull();
        assertThat(query.cursor()).isNull();
    }

    @Test
    void notificationQuery_rejectsNullUserId() {
        assertThatThrownBy(() -> new NotificationQuery(
                null,
                "tenant-1",
                null,
                null,
                null,
                10
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void notificationQuery_rejectsNullTenancyId() {
        assertThatThrownBy(() -> new NotificationQuery(
                "user-1",
                null,
                null,
                null,
                null,
                10
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenancyId");
    }

    @Test
    void notificationQuery_rejectsZeroLimit() {
        assertThatThrownBy(() -> new NotificationQuery(
                "user-1",
                "tenant-1",
                null,
                null,
                null,
                0
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be positive");
    }

    @Test
    void notificationQuery_rejectsNegativeLimit() {
        assertThatThrownBy(() -> new NotificationQuery(
                "user-1",
                "tenant-1",
                null,
                null,
                null,
                -5
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit must be positive");
    }

    // NotificationPage tests

    @Test
    void notificationPage_validConstruction() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var notification = new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                NotificationStatus.UNREAD,
                Instant.now(),
                null,
                null
        );
        var page = new NotificationPage(List.of(notification), "cursor-next");

        assertThat(page.notifications()).hasSize(1);
        assertThat(page.notifications().get(0)).isEqualTo(notification);
        assertThat(page.nextCursor()).isEqualTo("cursor-next");
    }

    @Test
    void notificationPage_acceptsNullCursor() {
        var page = new NotificationPage(List.of(), null);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void notificationPage_rejectsNullNotifications() {
        assertThatThrownBy(() -> new NotificationPage(null, "cursor"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("notifications");
    }

    @Test
    void notificationPage_makesDefensiveCopy() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var notification = new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                NotificationStatus.UNREAD,
                Instant.now(),
                null,
                null
        );
        var mutableList = new java.util.ArrayList<>(List.of(notification));
        var page = new NotificationPage(mutableList, null);

        // Mutate original list
        mutableList.clear();

        // Page's copy is unaffected
        assertThat(page.notifications()).hasSize(1);
    }

    @Test
    void notificationPage_returnsUnmodifiableList() {
        var page = new NotificationPage(List.of(), null);
        assertThatThrownBy(() -> page.notifications().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // CDI Event Tests

    @Test
    void notificationCreated_validConstruction() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var notification = new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                NotificationStatus.UNREAD,
                Instant.now(),
                null,
                null
        );
        var event = new NotificationCreated(notification);
        assertThat(event.notification()).isEqualTo(notification);
    }

    @Test
    void notificationCreated_rejectsNullNotification() {
        assertThatThrownBy(() -> new NotificationCreated(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("notification");
    }

    @Test
    void notificationStatusChanged_validConstruction() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var notification = new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                NotificationStatus.READ,
                Instant.now(),
                Instant.now(),
                null
        );
        var event = new NotificationStatusChanged(notification, NotificationStatus.UNREAD);
        assertThat(event.notification()).isEqualTo(notification);
        assertThat(event.previousStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    void notificationStatusChanged_rejectsNullNotification() {
        assertThatThrownBy(() -> new NotificationStatusChanged(null, NotificationStatus.UNREAD))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("notification");
    }

    @Test
    void notificationStatusChanged_rejectsNullPreviousStatus() {
        var source = new NotificationSource("evt-123", "work-item", "wi-456", "actor-789");
        var notification = new Notification(
                "notif-123",
                "user-1",
                "tenant-1",
                "Title",
                null,
                "category",
                NotificationSeverity.INFO,
                null,
                source,
                NotificationStatus.READ,
                Instant.now(),
                Instant.now(),
                null
        );
        assertThatThrownBy(() -> new NotificationStatusChanged(notification, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("previousStatus");
    }

    @Test
    void allNotificationsRead_validConstruction() {
        var event = new AllNotificationsRead("user-1", "tenant-1", 5);
        assertThat(event.userId()).isEqualTo("user-1");
        assertThat(event.tenancyId()).isEqualTo("tenant-1");
        assertThat(event.count()).isEqualTo(5);
    }

    @Test
    void allNotificationsRead_rejectsNullUserId() {
        assertThatThrownBy(() -> new AllNotificationsRead(null, "tenant-1", 5))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void allNotificationsRead_rejectsNullTenancyId() {
        assertThatThrownBy(() -> new AllNotificationsRead("user-1", null, 5))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenancyId");
    }
}
