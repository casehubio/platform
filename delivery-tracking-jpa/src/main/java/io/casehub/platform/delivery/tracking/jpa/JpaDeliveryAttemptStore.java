package io.casehub.platform.delivery.tracking.jpa;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptPage;
import io.casehub.platform.api.delivery.DeliveryAttemptQuery;
import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import io.casehub.platform.api.delivery.DeliveryStatus;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class JpaDeliveryAttemptStore implements DeliveryAttemptStore {

    private static final Logger LOG = Logger.getLogger(JpaDeliveryAttemptStore.class);

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "casehub.delivery.retry.claim-timeout", defaultValue = "5m")
    Duration claimTimeout;

    @ConfigProperty(name = "casehub.delivery.tracking.retention-days", defaultValue = "90")
    int retentionDays;

    @ConfigProperty(name = "casehub.delivery.tracking.failed-retention-days", defaultValue = "365")
    int failedRetentionDays;

    @Override
    @Transactional
    public void store(DeliveryAttempt attempt) {
        entityManager.persist(DeliveryAttemptEntity.fromDomain(attempt));
    }

    @Override
    @Transactional
    public void update(DeliveryAttempt attempt) {
        var entity = entityManager.find(DeliveryAttemptEntity.class, attempt.id());
        if (entity == null) {
            LOG.warnf("DeliveryAttempt %s not found for update", attempt.id());
            return;
        }
        entity.notificationId = attempt.notificationId();
        entity.channelId = attempt.channelId();
        entity.userId = attempt.userId();
        entity.tenancyId = attempt.tenancyId();
        entity.deliveryType = attempt.deliveryType();
        entity.status = attempt.status();
        entity.attemptCount = attempt.attemptCount();
        entity.lastAttemptedAt = attempt.lastAttemptedAt();
        entity.deliveredAt = attempt.deliveredAt();
        entity.nextRetryAt = attempt.nextRetryAt();
        entity.failureReason = attempt.failureReason();
        entity.payload = attempt.payload();
    }

    @Override
    @Transactional
    public List<DeliveryAttempt> claimRetryable(Instant now, int batchSize) {
        List<DeliveryAttemptEntity> entities = entityManager.createQuery(
                        "SELECT e FROM DeliveryAttemptEntity e " +
                                "WHERE e.status = :status AND e.nextRetryAt IS NOT NULL AND e.nextRetryAt <= :now " +
                                "ORDER BY e.nextRetryAt ASC", DeliveryAttemptEntity.class)
                .setParameter("status", DeliveryStatus.RETRYING)
                .setParameter("now", now)
                .setMaxResults(batchSize)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint("jakarta.persistence.lock.timeout", -2)
                .getResultList();

        Instant claimExpiry = now.plus(claimTimeout);
        for (DeliveryAttemptEntity entity : entities) {
            entity.nextRetryAt = claimExpiry;
        }
        entityManager.flush();

        return entities.stream().map(DeliveryAttemptEntity::toDomain).toList();
    }

    @Override
    public DeliveryAttemptPage find(DeliveryAttemptQuery query) {
        var sb = new StringBuilder("SELECT e FROM DeliveryAttemptEntity e WHERE e.tenancyId = :tenancyId");
        if (query.userId() != null) sb.append(" AND e.userId = :userId");
        if (query.channelId() != null) sb.append(" AND e.channelId = :channelId");
        if (query.status() != null) sb.append(" AND e.status = :status");

        if (query.cursor() != null) {
            String[] parts = query.cursor().split("\\|", 2);
            sb.append(" AND (e.createdAt < :cursorTime OR (e.createdAt = :cursorTime AND e.id < :cursorId))");
        }
        sb.append(" ORDER BY e.createdAt DESC, e.id DESC");

        var jpql = entityManager.createQuery(sb.toString(), DeliveryAttemptEntity.class);
        jpql.setParameter("tenancyId", query.tenancyId());
        if (query.userId() != null) jpql.setParameter("userId", query.userId());
        if (query.channelId() != null) jpql.setParameter("channelId", query.channelId());
        if (query.status() != null) jpql.setParameter("status", query.status());

        if (query.cursor() != null) {
            String[] parts = query.cursor().split("\\|", 2);
            jpql.setParameter("cursorTime", Instant.parse(parts[0]));
            jpql.setParameter("cursorId", parts[1]);
        }

        jpql.setMaxResults(query.limit() + 1);
        List<DeliveryAttemptEntity> results = jpql.getResultList();

        boolean hasMore = results.size() > query.limit();
        List<DeliveryAttemptEntity> page = hasMore ? results.subList(0, query.limit()) : results;

        String nextCursor = null;
        if (hasMore) {
            var last = page.getLast();
            nextCursor = last.createdAt.toString() + "|" + last.id;
        }

        return new DeliveryAttemptPage(
                page.stream().map(DeliveryAttemptEntity::toDomain).toList(),
                nextCursor);
    }

    @Override
    public List<DeliveryAttempt> findByNotificationId(String notificationId) {
        return entityManager.createQuery(
                        "SELECT e FROM DeliveryAttemptEntity e WHERE e.notificationId = :notificationId " +
                                "ORDER BY e.createdAt ASC", DeliveryAttemptEntity.class)
                .setParameter("notificationId", notificationId)
                .getResultList()
                .stream().map(DeliveryAttemptEntity::toDomain).toList();
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    void retentionPurge() {
        Instant deliveredCutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        Instant failedCutoff = Instant.now().minus(Duration.ofDays(failedRetentionDays));

        int purgedDelivered = entityManager.createQuery(
                        "DELETE FROM DeliveryAttemptEntity e WHERE e.status = :status AND e.createdAt < :cutoff")
                .setParameter("status", DeliveryStatus.DELIVERED)
                .setParameter("cutoff", deliveredCutoff)
                .executeUpdate();

        int purgedExpired = entityManager.createQuery(
                        "DELETE FROM DeliveryAttemptEntity e WHERE e.status = :status AND e.createdAt < :cutoff")
                .setParameter("status", DeliveryStatus.EXPIRED)
                .setParameter("cutoff", deliveredCutoff)
                .executeUpdate();

        int purgedFailed = entityManager.createQuery(
                        "DELETE FROM DeliveryAttemptEntity e WHERE e.status = :status AND e.createdAt < :cutoff")
                .setParameter("status", DeliveryStatus.FAILED)
                .setParameter("cutoff", failedCutoff)
                .executeUpdate();

        int purgedStaleRetrying = entityManager.createQuery(
                        "DELETE FROM DeliveryAttemptEntity e WHERE e.status = :status " +
                                "AND e.nextRetryAt IS NOT NULL AND e.nextRetryAt < :cutoff")
                .setParameter("status", DeliveryStatus.RETRYING)
                .setParameter("cutoff", deliveredCutoff)
                .executeUpdate();

        int purgedOrphanedPrePersist = entityManager.createQuery(
                        "DELETE FROM DeliveryAttemptEntity e WHERE e.status = :status " +
                                "AND e.nextRetryAt IS NULL AND e.createdAt < :cutoff")
                .setParameter("status", DeliveryStatus.RETRYING)
                .setParameter("cutoff", Instant.now().minus(claimTimeout))
                .executeUpdate();

        int total = purgedDelivered + purgedExpired + purgedFailed + purgedStaleRetrying + purgedOrphanedPrePersist;
        if (total > 0) {
            LOG.infof("Retention purge: %d delivered, %d expired, %d failed, %d stale retrying, %d orphaned pre-persist",
                    purgedDelivered, purgedExpired, purgedFailed, purgedStaleRetrying, purgedOrphanedPrePersist);
        }
    }
}
