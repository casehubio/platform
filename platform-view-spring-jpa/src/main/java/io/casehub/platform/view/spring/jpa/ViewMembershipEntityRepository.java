package io.casehub.platform.view.spring.jpa;

import io.casehub.platform.view.jpa.ViewMembershipEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ViewMembershipEntityRepository extends JpaRepository<ViewMembershipEntity, ViewMembershipEntity.Key> {

    List<ViewMembershipEntity> findBySubjectId(UUID subjectId);

    List<ViewMembershipEntity> findBySubjectIdIn(Set<UUID> subjectIds);

    @Query("SELECT DISTINCT e.subjectId FROM ViewMembershipEntity e WHERE e.viewId = ?1")
    List<UUID> findSubjectIdsByViewId(UUID viewId);

    @Modifying
    @Query("DELETE FROM ViewMembershipEntity e WHERE e.subjectId = ?1")
    int deleteBySubjectId(UUID subjectId);

    @Modifying
    @Query("DELETE FROM ViewMembershipEntity e WHERE e.viewId = ?1")
    int deleteByViewId(UUID viewId);
}
