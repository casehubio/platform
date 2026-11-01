package io.casehub.platform.authn.challenge.jpa;

import io.casehub.platform.api.authn.ChallengeRecord;
import io.casehub.platform.api.authn.ChallengeStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class JpaChallengeStore implements ChallengeStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(ChallengeRecord record) {
        em.persist(ChallengeEntity.fromRecord(record));
    }

    @Override
    @Transactional
    public Optional<ChallengeRecord> consume(String challengeId) {
        ChallengeEntity entity = em.find(ChallengeEntity.class, challengeId);
        if (entity == null) {
            return Optional.empty();
        }
        ChallengeRecord record = entity.toRecord();
        em.remove(entity);
        return Optional.of(record);
    }

    @Transactional
    public int purgeExpired() {
        return em.createQuery("DELETE FROM ChallengeEntity c WHERE c.expiresAt < :now")
                .setParameter("now", Instant.now())
                .executeUpdate();
    }
}
