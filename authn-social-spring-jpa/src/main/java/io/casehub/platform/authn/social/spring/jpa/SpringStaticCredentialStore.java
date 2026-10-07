package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import io.casehub.platform.authn.social.jpa.StaticCredentialEntity;
import io.casehub.platform.authn.social.jpa.StaticCredentialId;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class SpringStaticCredentialStore implements StaticCredentialStore {

    private final StaticCredentialRepository repo;

    public SpringStaticCredentialStore(StaticCredentialRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(StaticCredentialRecord record) {
        repo.save(StaticCredentialEntity.fromRecord(record));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
        return repo.findById(new StaticCredentialId(actorId, provider, tenancyId))
                .map(StaticCredentialEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
        return repo.findByActorIdAndTenancyId(actorId, tenancyId).stream()
                .map(StaticCredentialEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String actorId, String provider, String tenancyId) {
        repo.deleteByActorIdAndProviderAndTenancyId(actorId, provider, tenancyId);
    }
}
