package io.casehub.platform.delivery.tracking.spring.jpa;

import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.delivery.tracking.jpa.EngagementEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface EngagementEventEntityRepository extends JpaRepository<EngagementEventEntity, String> {

    List<EngagementEventEntity> findByAttemptIdAndTenancyIdOrderByRecordedAt(String attemptId, String tenancyId);

    @Query("SELECT e FROM EngagementEventEntity e WHERE e.sourceId = ?1 AND e.sourceType = ?2 AND e.tenancyId = ?3 ORDER BY e.recordedAt")
    List<EngagementEventEntity> findBySourceAndTenant(String sourceId, DeliverySourceType sourceType, String tenancyId);

    @Modifying
    @Query("DELETE FROM EngagementEventEntity e WHERE e.sourceType = ?1 AND e.recordedAt < ?2")
    int deleteBySourceTypeBefore(DeliverySourceType sourceType, Instant cutoff);
}
