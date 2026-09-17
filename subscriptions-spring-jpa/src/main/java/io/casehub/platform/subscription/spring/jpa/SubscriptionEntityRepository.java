package io.casehub.platform.subscription.spring.jpa;

import io.casehub.platform.subscription.jpa.SubscriptionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionEntityRepository extends JpaRepository<SubscriptionEntity, String> {

    @Query("SELECT e FROM SubscriptionEntity e WHERE e.id = ?1 AND e.tenancyId = ?2 AND (e.ownerId = ?3 OR e.scope = 'SYSTEM')")
    Optional<SubscriptionEntity> findByIdAndTenancyIdAndOwnerOrSystem(String id, String tenancyId, String ownerId);

    @Query("SELECT e FROM SubscriptionEntity e WHERE e.tenancyId = ?1 AND e.ownerId = ?2 AND e.scope = 'USER' ORDER BY e.createdAt DESC, e.id DESC")
    List<SubscriptionEntity> findByTenantAndOwner(String tenancyId, String ownerId, Pageable pageable);

    @Query("SELECT e FROM SubscriptionEntity e WHERE e.tenancyId = ?1 AND e.ownerId = ?2 AND e.scope = 'USER' AND e.enabled = ?3 ORDER BY e.createdAt DESC, e.id DESC")
    List<SubscriptionEntity> findByTenantAndOwnerAndEnabled(String tenancyId, String ownerId, boolean enabled, Pageable pageable);

    @Query("SELECT e FROM SubscriptionEntity e WHERE e.tenancyId = ?1 AND e.scope = 'SYSTEM' ORDER BY e.createdAt DESC, e.id DESC")
    List<SubscriptionEntity> findByTenantAndSystem(String tenancyId, Pageable pageable);

    @Query("SELECT e FROM SubscriptionEntity e WHERE e.tenancyId = ?1 AND e.scope = 'SYSTEM' AND e.enabled = ?2 ORDER BY e.createdAt DESC, e.id DESC")
    List<SubscriptionEntity> findByTenantAndSystemAndEnabled(String tenancyId, boolean enabled, Pageable pageable);

    @Query("SELECT e FROM SubscriptionEntity e WHERE e.tenancyId = ?1 AND e.ownerId = ?2 AND e.scope = 'USER' AND (e.createdAt < ?3 OR (e.createdAt = ?3 AND e.id < ?4)) ORDER BY e.createdAt DESC, e.id DESC")
    List<SubscriptionEntity> findByTenantAndOwnerAfterCursor(String tenancyId, String ownerId, Instant cursorCreatedAt, String cursorId, Pageable pageable);

    @Query("SELECT e FROM SubscriptionEntity e WHERE e.tenancyId = ?1 AND e.scope = 'SYSTEM' AND (e.createdAt < ?2 OR (e.createdAt = ?2 AND e.id < ?3)) ORDER BY e.createdAt DESC, e.id DESC")
    List<SubscriptionEntity> findByTenantAndSystemAfterCursor(String tenancyId, Instant cursorCreatedAt, String cursorId, Pageable pageable);

    List<SubscriptionEntity> findByEnabledTrue();
}
