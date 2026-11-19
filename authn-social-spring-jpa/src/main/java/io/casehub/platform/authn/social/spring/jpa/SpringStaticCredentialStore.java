package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.api.authn.CredentialState;
import io.casehub.platform.api.authn.CredentialType;
import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import io.casehub.platform.api.util.UUIDv7;
import io.casehub.platform.authn.social.jpa.CredentialHasher;
import io.casehub.platform.authn.social.jpa.StaticCredentialEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SpringStaticCredentialStore implements StaticCredentialStore {

    private final StaticCredentialRepository repo;
    private final Duration gracePeriodTtl;

    public SpringStaticCredentialStore(StaticCredentialRepository repo, Duration gracePeriodTtl) {
        this.repo = repo;
        this.gracePeriodTtl = gracePeriodTtl;
    }

    @Override
    @Transactional
    public void store(StaticCredentialRecord record) {
        repo.findByActorIdAndProviderAndTenancyIdAndStatus(
                record.actorId(), record.provider(), record.tenancyId(), CredentialState.ACTIVE)
                .ifPresent(repo::delete);
        repo.save(StaticCredentialEntity.fromRecord(record));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
        return repo.findByActorIdAndProviderAndTenancyIdAndStatus(
                actorId, provider, tenancyId, CredentialState.ACTIVE)
                .map(StaticCredentialEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
        return repo.findByActorIdAndTenancyIdAndStatus(actorId, tenancyId, CredentialState.ACTIVE)
                .stream()
                .map(StaticCredentialEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String actorId, String provider, String tenancyId) {
        repo.deleteByActorIdAndProviderAndTenancyId(actorId, provider, tenancyId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaticCredentialRecord> lookup(String credential) {
        String hash = CredentialHasher.sha256Hex(credential);
        return repo.findByCredentialHashAndStatusInAndNotExpired(
                hash,
                List.of(CredentialState.ACTIVE, CredentialState.GRACE_PERIOD),
                Instant.now())
                .map(StaticCredentialEntity::toRecord);
    }

    @Override
    @Transactional
    public StaticCredentialRecord rotate(String actorId, String provider,
            String tenancyId, String newCredential) {
        var now = Instant.now();
        var existing = repo.findByActorIdAndProviderAndTenancyIdAndStatusNot(
                actorId, provider, tenancyId, CredentialState.REVOKED);

        CredentialType activeType = CredentialType.API_KEY;
        for (var entity : existing) {
            if (entity.status == CredentialState.GRACE_PERIOD) {
                entity.status = CredentialState.REVOKED;
                entity.updatedAt = now;
                repo.save(entity);
            } else if (entity.status == CredentialState.ACTIVE) {
                activeType = entity.type;
                entity.status = CredentialState.GRACE_PERIOD;
                entity.expiresAt = now.plus(gracePeriodTtl);
                entity.updatedAt = now;
                repo.save(entity);
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
        repo.save(newEntity);

        return new StaticCredentialRecord(actorId, tenancyId, provider,
                newCredential, activeType, now, now,
                CredentialState.ACTIVE, null);
    }

    @Override
    @Transactional
    public void revoke(String actorId, String provider, String tenancyId) {
        repo.revokeByActorIdAndProviderAndTenancyId(
                actorId, provider, tenancyId,
                CredentialState.REVOKED, Instant.now());
    }
}
