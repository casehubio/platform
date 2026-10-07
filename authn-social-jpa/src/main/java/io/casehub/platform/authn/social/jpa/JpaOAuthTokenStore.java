package io.casehub.platform.authn.social.jpa;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class JpaOAuthTokenStore implements OAuthTokenStore {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void store(OAuthTokenRecord record) {
        em.merge(OAuthTokenEntity.fromRecord(record));
    }

    @Override
    public Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId) {
        OAuthTokenEntity entity = em.find(OAuthTokenEntity.class,
                new OAuthTokenId(actorId, provider, tenancyId));
        return entity != null ? Optional.of(entity.toRecord()) : Optional.empty();
    }

    @Override
    public List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId) {
        return em.createQuery(
                        "FROM OAuthTokenEntity WHERE actorId = :actorId AND tenancyId = :tenancyId",
                        OAuthTokenEntity.class)
                .setParameter("actorId", actorId)
                .setParameter("tenancyId", tenancyId)
                .getResultList()
                .stream()
                .map(OAuthTokenEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String actorId, String provider, String tenancyId) {
        em.createQuery(
                "DELETE FROM OAuthTokenEntity WHERE actorId = :actorId " +
                "AND provider = :provider AND tenancyId = :tenancyId")
                .setParameter("actorId", actorId)
                .setParameter("provider", provider)
                .setParameter("tenancyId", tenancyId)
                .executeUpdate();
    }

    @Override
    @Transactional
    public void updateTokens(String actorId, String provider, String tenancyId,
                             String accessToken, String refreshToken, Instant expiresAt) {
        OAuthTokenEntity entity = em.find(OAuthTokenEntity.class,
                new OAuthTokenId(actorId, provider, tenancyId));
        if (entity != null) {
            entity.accessToken = accessToken;
            entity.refreshToken = refreshToken;
            entity.expiresAt = expiresAt;
        }
    }
}
