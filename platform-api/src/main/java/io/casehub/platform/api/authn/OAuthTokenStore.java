package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OAuthTokenStore {
    void store(OAuthTokenRecord record);
    Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId);
    List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId);
    void delete(String actorId, String provider, String tenancyId);
    void updateTokens(String actorId, String provider, String tenancyId,
                      String accessToken, String refreshToken, Instant expiresAt);

    default void updateScopes(String actorId, String provider, String tenancyId,
                              Set<String> grantedScopes) {}

}
