package io.casehub.platform.delivery.tracking.jpa;

import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.api.delivery.DeliveryStatus;
import io.casehub.platform.api.delivery.DeliveryType;
import io.casehub.platform.api.delivery.EngagementType;
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
class DeliveryRetentionPurgeTest {

    @Inject
    JpaDeliveryAttemptStore store;
    @Inject
    EntityManager           em;

    @BeforeEach
    @Transactional
    void setUp() {
        em.createQuery("DELETE FROM EngagementEventEntity").executeUpdate();
        em.createQuery("DELETE FROM DeliveryAttemptEntity").executeUpdate();
    }

    @Test
    @Transactional
    void attemptRetentionPurge_removes_old_delivered_retains_recent() {
        insertAttempt(DeliveryStatus.DELIVERED, Instant.now().minus(60, ChronoUnit.DAYS));
        insertAttempt(DeliveryStatus.DELIVERED, Instant.now().minus(10, ChronoUnit.DAYS));

        store.attemptRetentionPurge();

        long remaining = em.createQuery("SELECT COUNT(e) FROM DeliveryAttemptEntity e", Long.class)
                           .getSingleResult();
        assertEquals(1, remaining);
    }

    @Test
    @Transactional
    void attemptRetentionPurge_failed_uses_longer_retention() {
        insertAttempt(DeliveryStatus.FAILED, Instant.now().minus(400, ChronoUnit.DAYS));
        insertAttempt(DeliveryStatus.FAILED, Instant.now().minus(100, ChronoUnit.DAYS));

        store.attemptRetentionPurge();

        long remaining = em.createQuery("SELECT COUNT(e) FROM DeliveryAttemptEntity e", Long.class)
                           .getSingleResult();
        assertEquals(1, remaining);
    }

    @Test
    @Transactional
    void engagementRetentionPurge_removes_old_engagement_events() {
        String attemptId = insertAttempt(DeliveryStatus.DELIVERED, Instant.now());
        insertEngagement(attemptId, Instant.now().minus(100, ChronoUnit.DAYS));
        insertEngagement(attemptId, Instant.now().minus(10, ChronoUnit.DAYS));

        store.engagementRetentionPurge();

        long remaining = em.createQuery("SELECT COUNT(e) FROM EngagementEventEntity e", Long.class)
                           .getSingleResult();
        assertEquals(1, remaining);
    }

    private String insertAttempt(DeliveryStatus status, Instant createdAt) {
        DeliveryAttemptEntity entity = new DeliveryAttemptEntity();
        entity.id              = UUID.randomUUID().toString();
        entity.sourceId        = "src-1";
        entity.sourceType      = DeliverySourceType.NOTIFICATION;
        entity.channelId       = "in_app";
        entity.userId          = "user1";
        entity.tenancyId       = "test-tenant";
        entity.deliveryType    = DeliveryType.IMMEDIATE;
        entity.status          = status;
        entity.attemptCount    = 1;
        entity.createdAt       = createdAt;
        entity.lastAttemptedAt = createdAt;
        entity.payload         = "{}";
        em.persist(entity);
        return entity.id;
    }

    private void insertEngagement(String attemptId, Instant recordedAt) {
        EngagementEventEntity entity = new EngagementEventEntity();
        entity.id         = UUID.randomUUID().toString();
        entity.attemptId  = attemptId;
        entity.sourceId   = "src-1";
        entity.sourceType = DeliverySourceType.NOTIFICATION;
        entity.channelId  = "in_app";
        entity.userId     = "user1";
        entity.tenancyId  = "test-tenant";
        entity.type       = EngagementType.OPENED;
        entity.recordedAt = recordedAt;
        em.persist(entity);
    }
}
