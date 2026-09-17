package io.casehub.platform.notification.spring.jpa;

import io.casehub.platform.api.notification.NotificationStatus;
import io.casehub.platform.notification.jpa.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationEntityRepository extends JpaRepository<NotificationEntity, String> {

    long countByUserIdAndTenancyIdAndStatus(String userId, String tenancyId, NotificationStatus status);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenant(String userId, String tenancyId, Pageable pageable);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.status = ?3 ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenantAndStatus(String userId, String tenancyId, NotificationStatus status, Pageable pageable);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.category = ?3 ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenantAndCategory(String userId, String tenancyId, String category, Pageable pageable);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.status = ?3 AND e.category = ?4 ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenantAndStatusAndCategory(String userId, String tenancyId, NotificationStatus status, String category, Pageable pageable);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND (e.createdAt < ?3 OR (e.createdAt = ?3 AND e.id < ?4)) ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenantAfterCursor(String userId, String tenancyId, Instant cursorCreatedAt, String cursorId, Pageable pageable);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.status = ?3 AND (e.createdAt < ?4 OR (e.createdAt = ?4 AND e.id < ?5)) ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenantAndStatusAfterCursor(String userId, String tenancyId, NotificationStatus status, Instant cursorCreatedAt, String cursorId, Pageable pageable);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.category = ?3 AND (e.createdAt < ?4 OR (e.createdAt = ?4 AND e.id < ?5)) ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenantAndCategoryAfterCursor(String userId, String tenancyId, String category, Instant cursorCreatedAt, String cursorId, Pageable pageable);

    @Query("SELECT e FROM NotificationEntity e WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.status = ?3 AND e.category = ?4 AND (e.createdAt < ?5 OR (e.createdAt = ?5 AND e.id < ?6)) ORDER BY e.createdAt DESC, e.id DESC")
    List<NotificationEntity> findByUserAndTenantAndStatusAndCategoryAfterCursor(String userId, String tenancyId, NotificationStatus status, String category, Instant cursorCreatedAt, String cursorId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndUserIdAndTenancyIdAndStatusNot(String id, String userId, String tenancyId, NotificationStatus status);

    @Modifying
    @Query("UPDATE NotificationEntity e SET e.status = ?4, e.readAt = ?5 WHERE e.userId = ?1 AND e.tenancyId = ?2 AND e.status = ?3")
    int markAllRead(String userId, String tenancyId, NotificationStatus fromStatus, NotificationStatus toStatus, Instant readAt);

    @Modifying
    @Query("DELETE FROM NotificationEntity e WHERE e.status IN (?1) AND e.createdAt < ?2")
    int deleteByStatusAndCreatedBefore(List<NotificationStatus> statuses, Instant cutoff);
}
