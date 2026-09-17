package io.casehub.platform.acl.spring.jpa;

import io.casehub.platform.acl.jpa.AclEntryEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface AclEntryEntityRepository extends JpaRepository<AclEntryEntity, Long> {

    List<AclEntryEntity> findByActorIdAndResourceIdAndTenancyId(
            String actorId, String resourceId, String tenancyId);

    @Query("SELECT e FROM AclEntryEntity e WHERE e.actorId = ?1 AND e.resourceId = ?2 AND e.action = ?3 AND e.tenancyId = ?4 AND e.entryType = ?5")
    List<AclEntryEntity> findByActorResourceActionType(
            String actorId, String resourceId, String action, String tenancyId, String entryType);

    @Modifying
    @Query("DELETE FROM AclEntryEntity e WHERE e.actorId = ?1 AND e.resourceId = ?2 AND e.action = ?3 AND e.tenancyId = ?4 AND e.entryType = ?5")
    int deleteByActorResourceActionType(
            String actorId, String resourceId, String action, String tenancyId, String entryType);

    @Modifying
    @Query("DELETE FROM AclEntryEntity e WHERE e.actorId = ?1 AND e.resourceId = ?2 AND e.tenancyId = ?3")
    int deleteByActorAndResource(String actorId, String resourceId, String tenancyId);

    @Query("SELECT COUNT(e) FROM AclEntryEntity e WHERE e.actorId IN ?1 AND e.resourceId = ?2 AND e.action IN ?3 AND e.entryType = ?4 AND (e.expiresAt IS NULL OR e.expiresAt > ?5) AND e.tenancyId = ?6")
    long countActiveByActorsResourceActionsTypeTenant(
            Collection<String> actorIds, String resourceId, List<String> actions,
            String entryType, Instant now, String tenancyId);

    @Query("SELECT COUNT(e) FROM AclEntryEntity e WHERE e.actorId IN ?1 AND e.resourceId = ?2 AND e.action IN ?3 AND e.entryType = ?4 AND (e.expiresAt IS NULL OR e.expiresAt > ?5)")
    long countActiveByActorsResourceActionsType(
            Collection<String> actorIds, String resourceId, List<String> actions,
            String entryType, Instant now);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\' AND e.tenancyId = ?5")
    List<String> findGrantedResourcesTenant(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix, String tenancyId);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\'")
    List<String> findGrantedResources(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\' AND e.tenancyId = ?5 AND e.resourceId > ?6 ORDER BY e.resourceId")
    List<String> findGrantedResourcesTenantCursor(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix,
            String tenancyId, String cursor, Pageable pageable);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\' AND e.resourceId > ?5 ORDER BY e.resourceId")
    List<String> findGrantedResourcesCursor(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix,
            String cursor, Pageable pageable);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\' AND e.tenancyId = ?5 ORDER BY e.resourceId")
    List<String> findGrantedResourcesTenantOrdered(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix,
            String tenancyId, Pageable pageable);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\' ORDER BY e.resourceId")
    List<String> findGrantedResourcesOrdered(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix, Pageable pageable);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'DENY' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\' AND e.tenancyId = ?5")
    List<String> findDeniedResourcesTenant(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix, String tenancyId);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'DENY' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.resourceId LIKE ?4 ESCAPE '\\'")
    List<String> findDeniedResources(
            List<String> actions, Instant now, Collection<String> actorIds, String prefix);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3 AND e.tenancyId = ?4")
    List<String> findAllGrantedResourcesTenant(
            List<String> actions, Instant now, Collection<String> actorIds, String tenancyId);

    @Query("SELECT DISTINCT e.resourceId FROM AclEntryEntity e WHERE e.entryType = 'ALLOW' AND e.action IN ?1 AND (e.expiresAt IS NULL OR e.expiresAt > ?2) AND e.actorId IN ?3")
    List<String> findAllGrantedResources(
            List<String> actions, Instant now, Collection<String> actorIds);

    @Modifying
    @Query("DELETE FROM AclEntryEntity e WHERE e.expiresAt IS NOT NULL AND e.expiresAt < ?1")
    int deleteExpired(Instant now);
}
