package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import io.casehub.platform.api.util.UUIDv7;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JpaStaticCredentialStore implements StaticCredentialStore {

    @Inject
    EntityManager em;

    @ConfigProperty(name = "casehub.authn.credential.grace-period-ttl", defaultValue = "PT24H")
    Duration gracePeriodTtl;

    @Override
    @Transactional
    public void store(StaticCredentialRecord record) {
        em.createQuery(
                "DELETE FROM StaticCredentialEntity WHERE actorId = :actorId " +
                "AND provider = :provider AND tenancyId = :tenancyId AND status = :status")
                .setParameter("actorId", record.actorId())
                .setParameter("provider", record.provider())
                .setParameter("tenancyId", record.tenancyId())
                .setParameter("status", CredentialState.ACTIVE)
                .executeUpdate();
        em.persist(StaticCredentialEntity.fromRecord(record));
    }

    @Override
    public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
        return em.createQuery(
                "FROM StaticCredentialEntity WHERE actorId = :actorId " +
                "AND provider = :provider AND tenancyId = :tenancyId AND status = :status",
                        StaticCredentialEntity.class)
                .setParameter("actorId", actorId)
                .setParameter("provider", provider)
                .setParameter("tenancyId", tenancyId)
                .setParameter("status", CredentialState.ACTIVE)
                .getResultStream()
                .findFirst()
                .map(StaticCredentialEntity::toRecord);
    }

    @Override
    public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
        return em.createQuery(
                "FROM StaticCredentialEntity WHERE actorId = :actorId " +
                "AND tenancyId = :tenancyId AND status = :status",
                        StaticCredentialEntity.class)
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .setParameter("status", CredentialState.ACTIVE)
                .getResultList()
                .stream()
                .map(StaticCredentialEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String actorId, String provider, String tenancyId) {
        em.createQuery(
                "DELETE FROM StaticCredentialEntity WHERE actorId = :actorId " +
                "AND provider = :provider AND tenancyId = :tenancyId")
                .setParameter("actorId", actorId)
                .setParameter("provider", provider)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }

    @Override
    public Optional<StaticCredentialRecord> lookup(String credential) {
        String hash = CredentialHasher.sha256Hex(credential);
        return em.createQuery(
                "FROM StaticCredentialEntity WHERE credentialHash = :hash " +
                "AND status IN (:active, :grace) " +
                "AND (expiresAt IS NULL OR expiresAt > :now)",
                        StaticCredentialEntity.class)
                .setParameter("hash", hash)
                .setParameter("active", CredentialState.ACTIVE)
                .setParameter("grace", CredentialState.GRACE_PERIOD)
                .setParameter("now", Instant.now())
                .getResultStream()
                .findFirst()
                .map(StaticCredentialEntity::toRecord);
    }

    @Override
    @Transactional
    public StaticCredentialRecord rotate(String actorId, String provider,
            String tenancyId, String newCredential) {
        var existing = em.createQuery(
                "FROM StaticCredentialEntity WHERE actorId = :actorId " +
                "AND provider = :provider AND tenancyId = :tenancyId " +
                "AND status != :revoked",
                        StaticCredentialEntity.class)
                .setParameter("actorId", actorId)
                .setParameter("provider", provider)
                .setParameter("tenancyId", tenancyId)
                .setParameter("revoked", CredentialState.REVOKED)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        var now = Instant.now();
        CredentialType activeType = CredentialType.API_KEY;

        for (var entity : existing) {
            if (entity.status == CredentialState.GRACE_PERIOD) {
                entity.status = CredentialState.REVOKED;
                entity.updatedAt = now;
            } else if (entity.status == CredentialState.ACTIVE) {
                activeType = entity.type;
                entity.status = CredentialState.GRACE_PERIOD;
                entity.expiresAt = now.plus(gracePeriodTtl);
                entity.updatedAt = now;
            }
        }

        var newEntity = new StaticCredentialEntity();
        newEntity.id = UUIDv7.generate();
        newEntity.actorId = actorId;
        newEntity.provider = provider;
        newEntity.tenancyId = tenancyId;
        newEntity.credentialHash = CredentialHasher.sha256Hex(newCredential);
        newEntity.type = activeType;
        newEntity.status = CredentialState.ACTIVE;
        newEntity.createdAt = now;
        newEntity.updatedAt = now;
        em.persist(newEntity);

        return new StaticCredentialRecord(actorId, tenancyId, provider,
                newCredential, activeType, now, now,
                CredentialState.ACTIVE, null);
    }

    @Override
    @Transactional
    public void revoke(String actorId, String provider, String tenancyId) {
        em.createQuery(
                "UPDATE StaticCredentialEntity SET status = :revoked, updatedAt = :now " +
                "WHERE actorId = :actorId AND provider = :provider " +
                "AND tenancyId = :tenancyId AND status != :revoked")
                .setParameter("revoked", CredentialState.REVOKED)
                .setParameter("now", Instant.now())
                .setParameter("actorId", actorId)
                .setParameter("provider", provider)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }
}
