package io.casehub.platform.registration.jpa;

import io.casehub.platform.registration.PasswordCredential;
import io.casehub.platform.registration.PasswordCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class JpaPasswordCredentialStore implements PasswordCredentialStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(PasswordCredential credential) {
        em.merge(PasswordCredentialEntity.fromRecord(credential));
    }

    @Override
    public Optional<PasswordCredential> findByActorId(String actorId, String tenancyId) {
        PasswordCredentialEntity entity = em.find(PasswordCredentialEntity.class,
                new PasswordCredentialEntityId(actorId, tenancyId));
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    @Transactional
    public void updatePassword(String actorId, String tenancyId, String hashedPassword) {
        PasswordCredentialEntity entity = em.find(PasswordCredentialEntity.class,
                new PasswordCredentialEntityId(actorId, tenancyId));
        if (entity != null) {
            entity.hashedPassword = hashedPassword;
            em.merge(entity);
        }
    }

    @Override
    @Transactional
    public void updateLastUsed(String actorId, String tenancyId) {
        PasswordCredentialEntity entity = em.find(PasswordCredentialEntity.class,
                new PasswordCredentialEntityId(actorId, tenancyId));
        if (entity != null) {
            entity.lastUsedAt = Instant.now();
            em.merge(entity);
        }
    }

    @Override
    @Transactional
    public void delete(String actorId, String tenancyId) {
        em.createQuery("DELETE FROM PasswordCredentialEntity e WHERE e.actorId = :actorId AND e.tenancyId = :tenancyId")
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }
}
