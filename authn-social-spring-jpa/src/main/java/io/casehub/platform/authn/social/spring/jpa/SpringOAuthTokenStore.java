package io.casehub.platform.authn.social.spring.jpa;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.casehub.platform.authn.social.jpa.OAuthTokenEntity;
import io.casehub.platform.authn.social.jpa.OAuthTokenId;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SpringOAuthTokenStore implements OAuthTokenStore {

    private final OAuthTokenRepository repo;

    public SpringOAuthTokenStore(OAuthTokenRepository repo) {
        this.repo = repo;
    }

    @Override
    @Transactional
    public void store(OAuthTokenRecord record) {
        repo.save(OAuthTokenEntity.fromRecord(record));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId) {
        return repo.findById(new OAuthTokenId(actorId, provider, tenancyId))
                .map(OAuthTokenEntity::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId) {
        return repo.findByActorIdAndTenancyId(actorId, tenancyId).stream()
                .map(OAuthTokenEntity::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String actorId, String provider, String tenancyId) {
        repo.deleteByActorIdAndProviderAndTenancyId(actorId, provider, tenancyId);
    }

    @Override
    @Transactional
    public void updateTokens(String actorId, String provider, String tenancyId,
                              String accessToken, String refreshToken, Instant expiresAt) {
        repo.findById(new OAuthTokenId(actorId, provider, tenancyId)).ifPresent(e -> {
            e.accessToken = accessToken;
            e.refreshToken = refreshToken;
            e.expiresAt = expiresAt;
            repo.save(e);
        });
    }
}
