package io.casehub.platform.authn.webauthn.jpa;

import io.casehub.platform.api.authn.WebAuthnCredential;
import io.casehub.platform.api.authn.WebAuthnCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JpaWebAuthnCredentialStore implements WebAuthnCredentialStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(WebAuthnCredential credential) {
        em.merge(WebAuthnCredentialEntity.fromRecord(credential));
    }

    @Override
    public Optional<WebAuthnCredential> findByCredentialId(String credentialId) {
        WebAuthnCredentialEntity entity = em.find(WebAuthnCredentialEntity.class, credentialId);
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    public List<WebAuthnCredential> findByActorId(String actorId, String tenancyId) {
        return em.createQuery(
                        "FROM WebAuthnCredentialEntity WHERE actorId = :actorId AND tenancyId = :tenancyId",
                        WebAuthnCredentialEntity.class)
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .getResultList()
                .stream()
                .map(WebAuthnCredentialEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void updateAfterAuthentication(String credentialId, long newSignCount, Instant lastUsedAt) {
        WebAuthnCredentialEntity entity = em.find(WebAuthnCredentialEntity.class, credentialId);
        if (entity != null) {
            entity.signCount = newSignCount;
            entity.lastUsedAt = lastUsedAt;
        }
    }

    @Override
    @Transactional
    public void delete(String credentialId) {
        em.createQuery("DELETE FROM WebAuthnCredentialEntity WHERE credentialId = :id")
                .setParameter("id", credentialId)
                .executeUpdate();
    }
}
