package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.StaticCredentialRecord;
import io.casehub.platform.api.authn.StaticCredentialStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JpaStaticCredentialStore implements StaticCredentialStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(StaticCredentialRecord record) {
        em.merge(StaticCredentialEntity.fromRecord(record));
    }

    @Override
    public Optional<StaticCredentialRecord> find(String actorId, String provider, String tenancyId) {
        StaticCredentialEntity entity = em.find(StaticCredentialEntity.class,
                new StaticCredentialId(actorId, provider, tenancyId));
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    public List<StaticCredentialRecord> findAll(String actorId, String tenancyId) {
        return em.createQuery(
                        "FROM StaticCredentialEntity WHERE actorId = :actorId AND tenancyId = :tenancyId",
                        StaticCredentialEntity.class)
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
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
}
