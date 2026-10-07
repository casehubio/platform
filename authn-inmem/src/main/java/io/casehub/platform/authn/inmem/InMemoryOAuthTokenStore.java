package io.casehub.platform.authn.inmem;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOAuthTokenStore implements OAuthTokenStore {

    private final ConcurrentHashMap<String, OAuthTokenRecord> tokens = new ConcurrentHashMap<>();

    @Override
    public void store(OAuthTokenRecord record) {
        tokens.put(key(record.actorId(), record.provider(), record.tenancyId()), record);
    }

    @Override
    public Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId) {
        return Optional.ofNullable(tokens.get(key(actorId, provider, tenancyId)));
    }

    @Override
    public List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId) {
        return tokens.values().stream()
                .filter(r -> r.actorId().equals(actorId) && r.tenancyId().equals(tenancyId))
                .toList();
    }

    @Override
    public void delete(String actorId, String provider, String tenancyId) {
        tokens.remove(key(actorId, provider, tenancyId));
    }

    @Override
    public void updateTokens(String actorId, String provider, String tenancyId,
                             String accessToken, String refreshToken, Instant expiresAt) {
        tokens.computeIfPresent(key(actorId, provider, tenancyId), (k, r) ->
                new OAuthTokenRecord(r.actorId(), r.tenancyId(), r.provider(),
                        accessToken, refreshToken, r.grantedScopes(), expiresAt, r.createdAt()));
    }

    @Override
    public void updateScopes(String actorId, String provider, String tenancyId,
                             Set<String> grantedScopes) {
        tokens.computeIfPresent(key(actorId, provider, tenancyId), (k, r) ->
                                                                           new OAuthTokenRecord(r.actorId(), r.tenancyId(), r.provider(),
                                                                                                r.accessToken(), r.refreshToken(), Set.copyOf(grantedScopes),
                                                                                                r.expiresAt(), r.createdAt()));
    }


    private static String key(String actorId, String provider, String tenancyId) {
        return actorId + "|" + provider + "|" + tenancyId;
    }

    public int size() {
        return tokens.size();
    }
}
