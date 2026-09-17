package io.casehub.platform.delivery.tracking.spring.jpa;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptPage;
import io.casehub.platform.api.delivery.DeliveryAttemptQuery;
import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.api.delivery.DeliveryStatus;
import io.casehub.platform.api.delivery.DeliveryType;
import io.casehub.platform.api.delivery.EngagementEvent;
import io.casehub.platform.api.delivery.EngagementType;
import io.casehub.platform.api.util.UUIDv7;
import io.casehub.platform.delivery.tracking.jpa.DeliveryAttemptEntity;
import io.casehub.platform.delivery.tracking.jpa.EngagementEventEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(SpringDeliveryAttemptStoreTest.TestConfig.class)
class SpringDeliveryAttemptStoreTest {

    private static final String TENANT = "test-tenant";

    @Configuration
    @AutoConfigurationPackage
    @EnableJpaRepositories(basePackageClasses = DeliveryAttemptEntityRepository.class)
    @EntityScan(basePackageClasses = {DeliveryAttemptEntity.class, EngagementEventEntity.class})
    static class TestConfig {
        @Bean
        SpringDeliveryAttemptStore springDeliveryAttemptStore(
                DeliveryAttemptEntityRepository attemptRepo,
                EngagementEventEntityRepository engagementRepo,
                EntityManager entityManager) {
            return new SpringDeliveryAttemptStore(attemptRepo, engagementRepo, entityManager, Duration.ofMinutes(5));
        }
    }

    @Autowired SpringDeliveryAttemptStore store;

    private DeliveryAttempt testAttempt(String id, DeliveryStatus status) {
        return new DeliveryAttempt(
                id, "source-1", DeliverySourceType.NOTIFICATION, "email",
                "user-1", TENANT, DeliveryType.IMMEDIATE, status, 1,
                Instant.now(), Instant.now(), null, null,
                null, "{\"title\":\"test\"}", null, null);
    }

    @Test
    void storeAndFindById() {
        String id = UUIDv7.generate();
        store.store(testAttempt(id, DeliveryStatus.DELIVERED));

        DeliveryAttempt found = store.findById(id);
        assertNotNull(found);
        assertEquals(id, found.id());
        assertEquals(DeliveryStatus.DELIVERED, found.status());
    }

    @Test
    void findByIdWithTenancy() {
        String id = UUIDv7.generate();
        store.store(testAttempt(id, DeliveryStatus.DELIVERED));

        assertNotNull(store.findById(id, TENANT));
        assertNull(store.findById(id, "wrong-tenant"));
    }

    @Test
    void update() {
        String id = UUIDv7.generate();
        store.store(testAttempt(id, DeliveryStatus.RETRYING));

        DeliveryAttempt updated = new DeliveryAttempt(
                id, "source-1", DeliverySourceType.NOTIFICATION, "email",
                "user-1", TENANT, DeliveryType.IMMEDIATE, DeliveryStatus.DELIVERED, 2,
                Instant.now(), Instant.now(), Instant.now(), null,
                null, "{\"title\":\"test\"}", null, null);
        store.update(updated);

        assertEquals(DeliveryStatus.DELIVERED, store.findById(id).status());
        assertEquals(2, store.findById(id).attemptCount());
    }

    @Test
    void findBySource() {
        String id1 = UUIDv7.generate();
        String id2 = UUIDv7.generate();
        store.store(testAttempt(id1, DeliveryStatus.DELIVERED));
        store.store(testAttempt(id2, DeliveryStatus.DELIVERED));

        List<DeliveryAttempt> found = store.findBySource("source-1", DeliverySourceType.NOTIFICATION, TENANT);
        assertEquals(2, found.size());
    }

    @Test
    void cursorPagination() {
        for (int i = 0; i < 5; i++) {
            store.store(testAttempt(UUIDv7.generate(), DeliveryStatus.DELIVERED));
        }

        DeliveryAttemptPage page1 = store.find(new DeliveryAttemptQuery(
                "user-1", TENANT, null, null, null, null, 3));
        assertEquals(3, page1.attempts().size());
        assertNotNull(page1.nextCursor());

        DeliveryAttemptPage page2 = store.find(new DeliveryAttemptQuery(
                "user-1", TENANT, null, null, null, page1.nextCursor(), 3));
        assertEquals(2, page2.attempts().size());
        assertNull(page2.nextCursor());
    }

    @Test
    void recordEngagementAndFind() {
        String attemptId = UUIDv7.generate();
        store.store(testAttempt(attemptId, DeliveryStatus.DELIVERED));

        EngagementEvent event = new EngagementEvent(
                UUIDv7.generate(), attemptId, "source-1", DeliverySourceType.NOTIFICATION,
                "email", "user-1", TENANT, EngagementType.OPENED, Instant.now(), null);
        store.recordEngagement(event);

        List<EngagementEvent> events = store.findEngagementsByAttemptId(attemptId, TENANT);
        assertEquals(1, events.size());
        assertEquals(EngagementType.OPENED, events.getFirst().type());
    }

    @Test
    void recordEngagementSetsFirstOpenedAt() {
        String attemptId = UUIDv7.generate();
        store.store(testAttempt(attemptId, DeliveryStatus.DELIVERED));

        Instant openedAt = Instant.now();
        EngagementEvent event = new EngagementEvent(
                UUIDv7.generate(), attemptId, "source-1", DeliverySourceType.NOTIFICATION,
                "email", "user-1", TENANT, EngagementType.OPENED, openedAt, null);
        store.recordEngagement(event);

        DeliveryAttempt attempt = store.findById(attemptId);
        assertNotNull(attempt.firstOpenedAt());
    }

    @Test
    void findEngagementsBySource() {
        String attemptId = UUIDv7.generate();
        store.store(testAttempt(attemptId, DeliveryStatus.DELIVERED));

        store.recordEngagement(new EngagementEvent(
                UUIDv7.generate(), attemptId, "source-1", DeliverySourceType.NOTIFICATION,
                "email", "user-1", TENANT, EngagementType.OPENED, Instant.now(), null));

        List<EngagementEvent> events = store.findEngagementsBySource(
                "source-1", DeliverySourceType.NOTIFICATION, TENANT);
        assertEquals(1, events.size());
    }

    @Test
    @Disabled("PostgreSQL-only — SELECT FOR UPDATE SKIP LOCKED")
    void claimRetryable() {
        String id = UUIDv7.generate();
        DeliveryAttempt retrying = new DeliveryAttempt(
                id, "source-1", DeliverySourceType.NOTIFICATION, "email",
                "user-1", TENANT, DeliveryType.IMMEDIATE, DeliveryStatus.RETRYING, 1,
                Instant.now(), Instant.now(), null, Instant.now().minusSeconds(60),
                "transient error", "{\"title\":\"test\"}", null, null);
        store.store(retrying);

        List<DeliveryAttempt> claimed = store.claimRetryable(Instant.now(), 10);
        assertEquals(1, claimed.size());
    }
}
