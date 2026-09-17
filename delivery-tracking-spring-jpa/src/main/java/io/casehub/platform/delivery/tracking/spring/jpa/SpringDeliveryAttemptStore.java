package io.casehub.platform.delivery.tracking.spring.jpa;

import io.casehub.platform.api.delivery.DeliveryAttempt;
import io.casehub.platform.api.delivery.DeliveryAttemptPage;
import io.casehub.platform.api.delivery.DeliveryAttemptQuery;
import io.casehub.platform.api.delivery.DeliveryAttemptStore;
import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.api.delivery.DeliveryStatus;
import io.casehub.platform.api.delivery.EngagementEvent;
import io.casehub.platform.api.delivery.EngagementType;
import io.casehub.platform.delivery.tracking.jpa.DeliveryAttemptEntity;
import io.casehub.platform.delivery.tracking.jpa.EngagementEventEntity;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class SpringDeliveryAttemptStore implements DeliveryAttemptStore {

    private static final Logger LOG = LoggerFactory.getLogger(SpringDeliveryAttemptStore.class);

    private final DeliveryAttemptEntityRepository attemptRepo;
    private final EngagementEventEntityRepository engagementRepo;
    private final EntityManager entityManager;
    private final Duration claimTimeout;

    public SpringDeliveryAttemptStore(
            DeliveryAttemptEntityRepository attemptRepo,
            EngagementEventEntityRepository engagementRepo,
            EntityManager entityManager,
            Duration claimTimeout) {
        this.attemptRepo = attemptRepo;
        this.engagementRepo = engagementRepo;
        this.entityManager = entityManager;
        this.claimTimeout = claimTimeout;
    }

    @Override
    @Transactional
    public void store(DeliveryAttempt attempt) {
        attemptRepo.save(DeliveryAttemptEntity.fromDomain(attempt));
    }

    @Override
    @Transactional
    public void update(DeliveryAttempt attempt) {
        var entity = attemptRepo.findById(attempt.id()).orElse(null);
        if (entity == null) {
            LOG.warn("DeliveryAttempt {} not found for update", attempt.id());
            return;
        }
        entity.sourceId = attempt.sourceId();
        entity.sourceType = attempt.sourceType();
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
        entity.firstOpenedAt = attempt.firstOpenedAt();
        entity.firstClickedAt = attempt.firstClickedAt();
        attemptRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAttempt findById(String id) {
        return attemptRepo.findById(id).map(DeliveryAttemptEntity::toDomain).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAttempt findById(String id, String tenancyId) {
        var entity = attemptRepo.findById(id).orElse(null);
        if (entity == null || !entity.tenancyId.equals(tenancyId)) return null;
        return entity.toDomain();
    }

    @Override
    @Transactional
    public List<DeliveryAttempt> claimRetryable(Instant now, int batchSize) {
        List<DeliveryAttemptEntity> entities = attemptRepo.findRetryable(
                DeliveryStatus.RETRYING, now, PageRequest.of(0, batchSize));

        Instant claimExpiry = now.plus(claimTimeout);
        for (DeliveryAttemptEntity entity : entities) {
            entity.nextRetryAt = claimExpiry;
        }
        attemptRepo.saveAll(entities);
        attemptRepo.flush();

        return entities.stream().map(DeliveryAttemptEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAttemptPage find(DeliveryAttemptQuery query) {
        var sb = new StringBuilder("SELECT e FROM DeliveryAttemptEntity e WHERE e.tenancyId = :tenancyId");
        if (query.userId() != null) sb.append(" AND e.userId = :userId");
        if (query.channelId() != null) sb.append(" AND e.channelId = :channelId");
        if (query.status() != null) sb.append(" AND e.status = :status");
        if (query.sourceType() != null) sb.append(" AND e.sourceType = :sourceType");

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
        if (query.sourceType() != null) jpql.setParameter("sourceType", query.sourceType());

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
            nextCursor = last.createdAt.truncatedTo(ChronoUnit.MICROS).toString() + "|" + last.id;
        }

        return new DeliveryAttemptPage(
                page.stream().map(DeliveryAttemptEntity::toDomain).toList(),
                nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAttempt> findBySource(String sourceId, DeliverySourceType sourceType, String tenancyId) {
        return attemptRepo.findBySourceAndTenant(sourceId, sourceType, tenancyId)
                .stream().map(DeliveryAttemptEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public void recordEngagement(EngagementEvent event) {
        engagementRepo.saveAndFlush(EngagementEventEntity.fromDomain(event));

        if (event.type() == EngagementType.OPENED) {
            attemptRepo.setFirstOpenedAt(event.attemptId(), event.recordedAt());
        }
        if (event.type() == EngagementType.CLICKED) {
            attemptRepo.setFirstClickedAt(event.attemptId(), event.recordedAt());
        }

        var cached = attemptRepo.findById(event.attemptId()).orElse(null);
        if (cached != null) {
            entityManager.refresh(cached);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EngagementEvent> findEngagementsByAttemptId(String attemptId, String tenancyId) {
        return engagementRepo.findByAttemptIdAndTenancyIdOrderByRecordedAt(attemptId, tenancyId)
                .stream().map(EngagementEventEntity::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EngagementEvent> findEngagementsBySource(String sourceId, DeliverySourceType sourceType, String tenancyId) {
        return engagementRepo.findBySourceAndTenant(sourceId, sourceType, tenancyId)
                .stream().map(EngagementEventEntity::toDomain).toList();
    }
}
