package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.authn.social.jpa.StaticCredentialEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StaticCredentialRepository extends JpaRepository<StaticCredentialEntity, String> {

    List<StaticCredentialEntity> findByActorIdAndTenancyIdAndStatus(
            String actorId, String tenancyId, CredentialState status);

    Optional<StaticCredentialEntity> findByActorIdAndProviderAndTenancyIdAndStatus(
            String actorId, String provider, String tenancyId, CredentialState status);

    @Query("SELECT e FROM StaticCredentialEntity e WHERE e.credentialHash = :hash " +
           "AND e.status IN :statuses AND (e.expiresAt IS NULL OR e.expiresAt > :now)")
    Optional<StaticCredentialEntity> findByCredentialHashAndStatusInAndNotExpired(
            String hash, List<CredentialState> statuses, Instant now);

    List<StaticCredentialEntity> findByActorIdAndProviderAndTenancyIdAndStatusNot(
            String actorId, String provider, String tenancyId, CredentialState status);

    @Modifying
    @Query("UPDATE StaticCredentialEntity SET status = :newStatus, updatedAt = :now " +
           "WHERE actorId = :actorId AND provider = :provider " +
           "AND tenancyId = :tenancyId AND status != :newStatus")
    int revokeByActorIdAndProviderAndTenancyId(
            String actorId, String provider, String tenancyId,
            CredentialState newStatus, Instant now);

    @Modifying
    void deleteByActorIdAndProviderAndTenancyId(
            String actorId, String provider, String tenancyId);
}
