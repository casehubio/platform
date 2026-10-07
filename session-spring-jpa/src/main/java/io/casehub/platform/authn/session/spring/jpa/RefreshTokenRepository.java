package io.casehub.platform.authn.session.spring.jpa;

import io.casehub.platform.authn.session.jpa.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    void deleteByFamilyId(String familyId);

    void deleteByActorIdAndTenancyId(String actorId, String tenancyId);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :before")
    int purgeExpired(@Param("before") Instant before);
}
