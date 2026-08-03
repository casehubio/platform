package io.casehub.platform.notification.jpa;

import io.casehub.platform.api.notification.NotificationSeverity;
import io.casehub.platform.api.notification.NotificationStatus;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class NotificationRetentionSchedulerTest {

    @Inject
    NotificationRetentionScheduler scheduler;
    @Inject
    EntityManager                  em;

    @BeforeEach
    @Transactional
    void setUp() {
        em.createQuery("DELETE FROM NotificationEntity").executeUpdate();
    }

    @Test
    @Transactional
    void purge_removes_old_read_notifications_retains_recent() {
        insertNotification(NotificationStatus.READ, Instant.now().minus(100, ChronoUnit.DAYS));
        insertNotification(NotificationStatus.READ, Instant.now().minus(10, ChronoUnit.DAYS));

        scheduler.purge();

        long remaining = em.createQuery("SELECT COUNT(n) FROM NotificationEntity n", Long.class)
                           .getSingleResult();
        assertEquals(1, remaining);
    }

    @Test
    @Transactional
    void purge_removes_old_dismissed_notifications() {
        insertNotification(NotificationStatus.DISMISSED, Instant.now().minus(100, ChronoUnit.DAYS));
        insertNotification(NotificationStatus.DISMISSED, Instant.now().minus(10, ChronoUnit.DAYS));

        scheduler.purge();

        long remaining = em.createQuery("SELECT COUNT(n) FROM NotificationEntity n", Long.class)
                           .getSingleResult();
        assertEquals(1, remaining);
    }

    @Test
    @Transactional
    void purge_removes_old_unread_notifications_at_longer_retention() {
        insertNotification(NotificationStatus.UNREAD, Instant.now().minus(400, ChronoUnit.DAYS));
        insertNotification(NotificationStatus.UNREAD, Instant.now().minus(100, ChronoUnit.DAYS));

        scheduler.purge();

        long remaining = em.createQuery("SELECT COUNT(n) FROM NotificationEntity n", Long.class)
                           .getSingleResult();
        assertEquals(1, remaining);
    }

    @Test
    @Transactional
    void purge_no_old_notifications_deletes_nothing() {
        insertNotification(NotificationStatus.READ, Instant.now());
        insertNotification(NotificationStatus.UNREAD, Instant.now());

        scheduler.purge();

        long remaining = em.createQuery("SELECT COUNT(n) FROM NotificationEntity n", Long.class)
                           .getSingleResult();
        assertEquals(2, remaining);
    }

    private void insertNotification(NotificationStatus status, Instant createdAt) {
        NotificationEntity entity = new NotificationEntity();
        entity.id               = UUID.randomUUID().toString();
        entity.userId           = "user1";
        entity.tenancyId        = "test-tenant";
        entity.title            = "Test";
        entity.body             = "Test body";
        entity.category         = "test";
        entity.severity         = NotificationSeverity.INFO;
        entity.sourceEventId    = "evt-1";
        entity.sourceEntityType = "case";
        entity.sourceEntityId   = "case-1";
        entity.sourceActorId    = "actor-1";
        entity.status           = status;
        entity.createdAt        = createdAt;
        em.persist(entity);
    }
}
