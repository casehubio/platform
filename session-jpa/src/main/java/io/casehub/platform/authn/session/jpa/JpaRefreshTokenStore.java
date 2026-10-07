package io.casehub.platform.authn.session.jpa;

import io.casehub.platform.api.authn.RefreshTokenRecord;
import io.casehub.platform.api.authn.RefreshTokenStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class JpaRefreshTokenStore implements RefreshTokenStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(RefreshTokenRecord record) {
        em.merge(RefreshTokenEntity.fromRecord(record));
    }

    @Override
    public Optional<RefreshTokenRecord> findByToken(String token) {
        RefreshTokenEntity entity = em.find(RefreshTokenEntity.class, token);
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    @Transactional
    public void consume(String token) {
        RefreshTokenEntity entity = em.find(RefreshTokenEntity.class, token);
        if (entity != null) {
            entity.consumed = true;
        }
    }

    @Override
    @Transactional
    public void revokeFamily(String familyId) {
        em.createQuery("DELETE FROM RefreshTokenEntity WHERE familyId = :familyId")
                .setParameter("familyId", familyId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void revokeByActorId(String actorId, String tenancyId) {
        em.createQuery("DELETE FROM RefreshTokenEntity WHERE actorId = :actorId AND tenancyId = :tenancyId")
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public int purgeExpired(Instant before) {
        return em.createQuery("DELETE FROM RefreshTokenEntity WHERE expiresAt < :before")
                .setParameter("before", before)
                .executeUpdate();
    }
}
