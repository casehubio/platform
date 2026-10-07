package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.IdentityBinding;
import io.casehub.platform.api.authn.IdentityBindingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.util.Optional;

@ApplicationScoped
public class JpaIdentityBindingStore implements IdentityBindingStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void bind(IdentityBinding binding) {
        em.merge(IdentityBindingEntity.fromRecord(binding));
    }

    @Override
    public Optional<IdentityBinding> findByExternalId(String provider, String externalId, String tenancyId) {
        IdentityBindingEntity entity = em.find(IdentityBindingEntity.class,
                new IdentityBindingId(provider, externalId, tenancyId));
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    public Optional<IdentityBinding> findByActorId(String actorId, String provider, String tenancyId) {
        return em.createQuery(
                        "FROM IdentityBindingEntity WHERE actorId = :actorId " +
                        "AND provider = :provider AND tenancyId = :tenancyId",
                        IdentityBindingEntity.class)
                .setParameter("actorId", actorId)
                .setParameter("provider", provider)
                .setParameter("tenancyId", tenancyId)
                .getResultStream()
                .findFirst()
                .map(IdentityBindingEntity::toRecord);
    }

    @Override
    @Transactional
    public void unbind(String provider, String externalId, String tenancyId) {
        em.createQuery(
                "DELETE FROM IdentityBindingEntity WHERE provider = :provider " +
                "AND externalId = :externalId AND tenancyId = :tenancyId")
                .setParameter("provider", provider)
                .setParameter("externalId", externalId)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }
}
