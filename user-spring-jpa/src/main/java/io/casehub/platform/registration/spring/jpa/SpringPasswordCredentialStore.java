package io.casehub.platform.registration.spring.jpa;

import io.casehub.platform.registration.PasswordCredential;
import io.casehub.platform.registration.PasswordCredentialStore;
import io.casehub.platform.registration.jpa.PasswordCredentialEntity;
import io.casehub.platform.registration.jpa.PasswordCredentialEntityId;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class SpringPasswordCredentialStore implements PasswordCredentialStore {

    private final PasswordCredentialEntityRepository repo;

    public SpringPasswordCredentialStore(PasswordCredentialEntityRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(PasswordCredential credential) {
        repo.save(PasswordCredentialEntity.fromRecord(credential));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordCredential> findByActorId(String actorId, String tenancyId) {
        return repo.findById(new PasswordCredentialEntityId(actorId, tenancyId))
                .map(PasswordCredentialEntity::toRecord);
    }

    @Override
    @Transactional
    public void updatePassword(String actorId, String tenancyId, String hashedPassword) {
        repo.findById(new PasswordCredentialEntityId(actorId, tenancyId))
                .ifPresent(e -> { e.hashedPassword = hashedPassword; repo.save(e); });
    }

    @Override
    @Transactional
    public void updateLastUsed(String actorId, String tenancyId) {
        repo.findById(new PasswordCredentialEntityId(actorId, tenancyId))
                .ifPresent(e -> { e.lastUsedAt = Instant.now(); repo.save(e); });
    }

    @Override
    @Transactional
    public void delete(String actorId, String tenancyId) {
        repo.deleteByActorIdAndTenancyId(actorId, tenancyId);
    }
}
