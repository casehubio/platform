package io.casehub.platform.view.spring.jpa;

import io.casehub.platform.view.jpa.SubjectViewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SubjectViewEntityRepository extends JpaRepository<SubjectViewEntity, UUID> {

    List<SubjectViewEntity> findByTenancyId(String tenancyId);

    @Query("SELECT DISTINCT e.tenancyId FROM SubjectViewEntity e")
    List<String> findDistinctTenancyIds();
}
