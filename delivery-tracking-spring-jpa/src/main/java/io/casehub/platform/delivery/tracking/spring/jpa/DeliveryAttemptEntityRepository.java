package io.casehub.platform.delivery.tracking.spring.jpa;

import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.api.delivery.DeliveryStatus;
import io.casehub.platform.delivery.tracking.jpa.DeliveryAttemptEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;

public interface DeliveryAttemptEntityRepository extends JpaRepository<DeliveryAttemptEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM DeliveryAttemptEntity e WHERE e.status = ?1 AND e.nextRetryAt IS NOT NULL AND e.nextRetryAt <= ?2 ORDER BY e.nextRetryAt ASC")
    List<DeliveryAttemptEntity> findRetryable(DeliveryStatus status, Instant now, Pageable pageable);

    @Query("SELECT e FROM DeliveryAttemptEntity e WHERE e.sourceId = ?1 AND e.sourceType = ?2 AND e.tenancyId = ?3 ORDER BY e.createdAt ASC")
    List<DeliveryAttemptEntity> findBySourceAndTenant(String sourceId, DeliverySourceType sourceType, String tenancyId);

    @Modifying
    @Query("UPDATE DeliveryAttemptEntity e SET e.firstOpenedAt = ?2 WHERE e.id = ?1 AND e.firstOpenedAt IS NULL")
    int setFirstOpenedAt(String id, Instant ts);

    @Modifying
    @Query("UPDATE DeliveryAttemptEntity e SET e.firstClickedAt = ?2 WHERE e.id = ?1 AND e.firstClickedAt IS NULL")
    int setFirstClickedAt(String id, Instant ts);

    @Modifying
    @Query("DELETE FROM DeliveryAttemptEntity e WHERE e.sourceType = ?1 AND e.status = ?2 AND e.createdAt < ?3")
    int deleteBySourceTypeAndStatusBefore(DeliverySourceType sourceType, DeliveryStatus status, Instant cutoff);

    @Modifying
    @Query("DELETE FROM DeliveryAttemptEntity e WHERE e.sourceType = ?1 AND e.status = ?2 AND e.nextRetryAt IS NOT NULL AND e.nextRetryAt < ?3")
    int deleteStaleRetrying(DeliverySourceType sourceType, DeliveryStatus status, Instant cutoff);

    @Modifying
    @Query("DELETE FROM DeliveryAttemptEntity e WHERE e.status = ?1 AND e.nextRetryAt IS NULL AND e.createdAt < ?2")
    int deleteOrphanedPrePersist(DeliveryStatus status, Instant cutoff);
}
