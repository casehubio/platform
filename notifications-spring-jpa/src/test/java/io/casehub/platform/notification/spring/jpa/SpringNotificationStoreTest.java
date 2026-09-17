package io.casehub.platform.notification.spring.jpa;

import io.casehub.platform.api.notification.Notification;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.NotificationPage;
import io.casehub.platform.api.notification.NotificationQuery;
import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationSource;
import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.notification.jpa.NotificationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringNotificationStoreTest.TestConfig.class)
class SpringNotificationStoreTest {

    private static final String USER = "user-1";
    private static final String TENANT = "test-tenant";

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = NotificationEntityRepository.class)
    @EntityScan(basePackageClasses = NotificationEntity.class)
    static class TestConfig {
        @Bean
        SpringNotificationStore springNotificationStore(
                NotificationEntityRepository repo,
                ApplicationEventPublisher events) {
            return new SpringNotificationStore(repo, events);
        }
    }

    @Autowired SpringNotificationStore store;

    private NotificationInput testInput(String title) {
        return new NotificationInput(
                USER, TENANT, title, "body", "test.event",
                NotificationSeverity.INFO, null,
                new NotificationSource("evt-1", "case", "case-1", "actor-1"));
    }

    @Test
    void storeAndFind() {
        store.store(testInput("Alert 1"));
        store.store(testInput("Alert 2"));

        NotificationPage page = store.find(new NotificationQuery(USER, TENANT, null, null, null, 10));
        assertEquals(2, page.notifications().size());
    }

    @Test
    void storeAll() {
        List<Notification> notifications = store.storeAll(List.of(
                testInput("A"), testInput("B"), testInput("C")));
        assertEquals(3, notifications.size());
    }

    @Test
    void unreadCount() {
        store.store(testInput("A"));
        store.store(testInput("B"));

        assertEquals(2, store.unreadCount(USER, TENANT));
    }

    @Test
    void markRead() {
        Notification n = store.store(testInput("Alert"));
        Optional<Notification> read = store.markRead(n.id(), USER, TENANT);

        assertTrue(read.isPresent());
        assertEquals(NotificationStatus.READ, read.get().status());
        assertNotNull(read.get().readAt());
        assertEquals(1, store.unreadCount(USER, TENANT) + 1);
    }

    @Test
    void dismiss() {
        Notification n = store.store(testInput("Alert"));
        Optional<Notification> dismissed = store.dismiss(n.id(), USER, TENANT);

        assertTrue(dismissed.isPresent());
        assertEquals(NotificationStatus.DISMISSED, dismissed.get().status());
        assertNotNull(dismissed.get().dismissedAt());
    }

    @Test
    void markAllRead() {
        store.store(testInput("A"));
        store.store(testInput("B"));
        store.store(testInput("C"));

        int count = store.markAllRead(USER, TENANT);
        assertEquals(3, count);
        assertEquals(0, store.unreadCount(USER, TENANT));
    }

    @Test
    void cursorPagination() {
        for (int i = 0; i < 5; i++) {
            store.store(testInput("Alert " + i));
        }

        NotificationPage page1 = store.find(new NotificationQuery(USER, TENANT, null, null, null, 3));
        assertEquals(3, page1.notifications().size());
        assertNotNull(page1.nextCursor());

        NotificationPage page2 = store.find(new NotificationQuery(USER, TENANT, null, null, page1.nextCursor(), 3));
        assertEquals(2, page2.notifications().size());
        assertNull(page2.nextCursor());
    }

    @Test
    void dismissedNotificationCannotBeMarkedRead() {
        Notification n = store.store(testInput("Alert"));
        store.dismiss(n.id(), USER, TENANT);

        Optional<Notification> read = store.markRead(n.id(), USER, TENANT);
        assertTrue(read.isEmpty());
    }
}
