package io.casehub.platform.acl.spring.jpa;

import io.casehub.platform.acl.jpa.AclAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface AclAuditLogEntityRepository extends JpaRepository<AclAuditLogEntity, Long> {

    @Modifying
    @Query("DELETE FROM AclAuditLogEntity e WHERE e.performedAt < ?1")
    int deleteBefore(Instant cutoff);
}
