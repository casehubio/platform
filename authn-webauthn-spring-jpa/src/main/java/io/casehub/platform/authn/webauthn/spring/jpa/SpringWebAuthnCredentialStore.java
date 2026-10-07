package io.casehub.platform.authn.webauthn.spring.jpa;

import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import io.casehub.platform.authn.webauthn.jpa.WebAuthnCredentialEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SpringWebAuthnCredentialStore implements WebAuthnCredentialStore {

    private final WebAuthnCredentialRepository repo;

    public SpringWebAuthnCredentialStore(WebAuthnCredentialRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(WebAuthnCredential credential) {
        repo.save(WebAuthnCredentialEntity.fromRecord(credential));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WebAuthnCredential> findByCredentialId(String credentialId) {
        return repo.findById(credentialId).map(WebAuthnCredentialEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WebAuthnCredential> findByActorId(String actorId, String tenancyId) {
        return repo.findByActorIdAndTenancyId(actorId, tenancyId).stream()
                .map(WebAuthnCredentialEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void updateAfterAuthentication(String credentialId, long newSignCount, Instant lastUsedAt) {
        repo.findById(credentialId).ifPresent(e -> {
            e.signCount = newSignCount;
            e.lastUsedAt = lastUsedAt;
            repo.save(e);
        });
    }

    @Override
    @Transactional
    public void delete(String credentialId) {
        repo.deleteById(credentialId);
    }
}
