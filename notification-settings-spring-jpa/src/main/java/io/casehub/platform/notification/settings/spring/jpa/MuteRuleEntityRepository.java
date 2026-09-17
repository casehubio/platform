package io.casehub.platform.notification.settings.spring.jpa;

import io.casehub.platform.notification.settings.jpa.MuteRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface MuteRuleEntityRepository extends JpaRepository<MuteRuleEntity, String> {

    @Query("SELECT m FROM MuteRuleEntity m WHERE m.userId = ?1 AND m.tenancyId = ?2 AND (m.expiresAt IS NULL OR m.expiresAt > ?3)")
    List<MuteRuleEntity> findActiveMutes(String userId, String tenancyId, Instant now);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM MuteRuleEntity m WHERE m.id = ?1 AND m.userId = ?2 AND m.tenancyId = ?3")
    int deleteByIdAndUserIdAndTenancyId(String id, String userId, String tenancyId);

    @Modifying
    @Query("DELETE FROM MuteRuleEntity m WHERE m.expiresAt IS NOT NULL AND m.expiresAt < ?1")
    int deleteExpired(Instant now);
}
