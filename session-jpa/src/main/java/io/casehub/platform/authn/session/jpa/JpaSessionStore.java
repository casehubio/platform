package io.casehub.platform.authn.session.jpa;

import io.casehub.platform.api.authn.SessionRecord;
import io.casehub.platform.api.authn.SessionStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class JpaSessionStore implements SessionStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(SessionRecord session) {
        em.merge(SessionEntity.fromRecord(session));
    }

    @Override
    public Optional<SessionRecord> findById(String sessionId) {
        SessionEntity entity = em.find(SessionEntity.class, sessionId);
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    @Transactional
    public void delete(String sessionId) {
        em.createQuery("DELETE FROM SessionEntity WHERE sessionId = :id")
                .setParameter("id", sessionId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void deleteByActorId(String actorId, String tenancyId) {
        em.createQuery("DELETE FROM SessionEntity WHERE actorId = :actorId AND tenancyId = :tenancyId")
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }
}
