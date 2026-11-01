package io.casehub.platform.authn.challenge.spring.jpa;

import io.casehub.platform.authn.challenge.jpa.ChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface ChallengeEntityRepository extends JpaRepository<ChallengeEntity, String> {

    @Modifying
    @Query("DELETE FROM ChallengeEntity c WHERE c.expiresAt < ?1")
    int deleteExpiredBefore(Instant cutoff);
}
