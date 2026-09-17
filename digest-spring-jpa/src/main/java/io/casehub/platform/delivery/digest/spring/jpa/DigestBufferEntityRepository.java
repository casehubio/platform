package io.casehub.platform.delivery.digest.spring.jpa;

import io.casehub.platform.delivery.digest.jpa.DigestBufferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DigestBufferEntityRepository extends JpaRepository<DigestBufferEntity, UUID> {

    List<DigestBufferEntity> findByUserIdAndTenancyIdAndChannelIdOrderByBufferedAtAsc(
            String userId, String tenancyId, String channelId);

    long countByUserIdAndTenancyIdAndChannelId(String userId, String tenancyId, String channelId);

    @Query("SELECT e.id FROM DigestBufferEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.channelId = ?3 ORDER BY e.bufferedAt ASC")
    List<UUID> findOldestIds(String userId, String tenancyId, String channelId);

    @Modifying
    @Query("DELETE FROM DigestBufferEntity e WHERE e.id IN ?1")
    int deleteByIds(List<UUID> ids);

    @Modifying
    @Query("DELETE FROM DigestBufferEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.channelId = ?3")
    int deleteByKey(String userId, String tenancyId, String channelId);

    @Query("SELECT DISTINCT e.userId, e.tenancyId, e.channelId FROM DigestBufferEntity e")
    List<Object[]> findDistinctKeys();

    @Query("SELECT DISTINCT e.userId, e.tenancyId, e.channelId FROM DigestBufferEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2")
    List<Object[]> findDistinctKeysByUser(String userId, String tenancyId);

    @Query("SELECT MIN(e.bufferedAt) FROM DigestBufferEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.channelId = ?3")
    Instant findOldestTimestamp(String userId, String tenancyId, String channelId);

    @Modifying
    @Query("DELETE FROM DigestBufferEntity e WHERE e.bufferedAt < ?1 AND NOT EXISTS (SELECT 1 FROM DigestBufferEntity recent WHERE recent.userId = e.userId AND recent.tenancyId = e.tenancyId AND recent.channelId = e.channelId AND recent.bufferedAt >= ?1)")
    int deleteOrphansBefore(Instant cutoff);
}
