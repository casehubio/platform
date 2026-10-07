package io.casehub.platform.authn;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@DefaultBean
@ApplicationScoped
public class NoOpOAuthTokenStore implements OAuthTokenStore {

    @Override
    public void store(OAuthTokenRecord record) {}

    @Override
    public Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId) {
        return Optional.empty();
    }

    @Override
    public List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId) {
        return List.of();
    }

    @Override
    public void delete(String actorId, String provider, String tenancyId) {}

    @Override
    public void updateTokens(String actorId, String provider, String tenancyId,
                             String accessToken, String refreshToken, Instant expiresAt) {}

    @Override
    public void updateScopes(String actorId, String provider, String tenancyId,
                             java.util.Set<String> grantedScopes) {}

}
