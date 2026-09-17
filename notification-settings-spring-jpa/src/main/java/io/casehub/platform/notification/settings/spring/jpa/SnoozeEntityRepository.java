package io.casehub.platform.notification.settings.spring.jpa;

import io.casehub.platform.notification.settings.jpa.SnoozeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface SnoozeEntityRepository extends JpaRepository<SnoozeEntity, SnoozeEntity.SnoozePK> {

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM SnoozeEntity s WHERE s.userId = ?1 AND s.tenancyId = ?2")
    int deleteByUserIdAndTenancyId(String userId, String tenancyId);

    @Modifying
    @Query("DELETE FROM SnoozeEntity s WHERE s.until < ?1")
    int deleteExpired(Instant now);
}
