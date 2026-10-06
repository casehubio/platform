package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthTokenManagerCoreTest {

    private static final String ACTOR = "user-1";
    private static final String TENANT = "tenant-1";
    private static final String PROVIDER = "google";

    private InMemOAuthTokenStore tokenStore;
    private StubRefreshClient refreshClient;
    private OAuthTokenManagerCore manager;

    @BeforeEach
    void setUp() {
        tokenStore = new InMemOAuthTokenStore();
        refreshClient = new StubRefreshClient();
        manager = new OAuthTokenManagerCore(tokenStore, refreshClient);
    }

    @Test
    void getValidTokenReturnsNonExpiredToken() {
        var token = new OAuthTokenRecord(
                ACTOR, TENANT, PROVIDER, "valid-token", "refresh",
                Set.of("openid"), Instant.now().plusSeconds(3600), Instant.now());
        tokenStore.store(token);

        var result = manager.getValidToken(ACTOR, PROVIDER, TENANT);

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("valid-token");
    }

    @Test
    void getValidTokenRefreshesExpiredToken() {
        var expired = new OAuthTokenRecord(
                ACTOR, TENANT, PROVIDER, "old-token", "refresh-token",
                Set.of("openid"), Instant.now().minusSeconds(60), Instant.now());
        tokenStore.store(expired);

        refreshClient.nextResponse = new OAuthTokenResponse(
                "new-token", "new-refresh", null, 3600, "Bearer", "openid");

        var result = manager.getValidToken(ACTOR, PROVIDER, TENANT);

        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("new-token");
        assertThat(refreshClient.lastRefreshToken).isEqualTo("refresh-token");
    }

    @Test
    void getValidTokenReturnsEmptyWhenNoToken() {
        var result = manager.getValidToken(ACTOR, PROVIDER, TENANT);
        assertThat(result).isEmpty();
    }

    @Test
    void getValidTokenReturnsEmptyWhenExpiredNoRefreshToken() {
        var expired = new OAuthTokenRecord(
                ACTOR, TENANT, PROVIDER, "old-token", null,
                Set.of("openid"), Instant.now().minusSeconds(60), Instant.now());
        tokenStore.store(expired);

        var result = manager.getValidToken(ACTOR, PROVIDER, TENANT);
        assertThat(result).isEmpty();
    }

    @Test
    void getValidTokenReturnsEmptyWhenRefreshFails() {
        var expired = new OAuthTokenRecord(
                ACTOR, TENANT, PROVIDER, "old-token", "refresh-token",
                Set.of("openid"), Instant.now().minusSeconds(60), Instant.now());
        tokenStore.store(expired);

        refreshClient.failOnRefresh = true;

        var result = manager.getValidToken(ACTOR, PROVIDER, TENANT);
        assertThat(result).isEmpty();
    }

    @Test
    void getValidTokenReturnsNullExpiryAsValid() {
        var token = new OAuthTokenRecord(
                ACTOR, TENANT, PROVIDER, "forever-token", null,
                Set.of(), null, Instant.now());
        tokenStore.store(token);

        var result = manager.getValidToken(ACTOR, PROVIDER, TENANT);
        assertThat(result).isPresent();
        assertThat(result.get().accessToken()).isEqualTo("forever-token");
    }

    @Test
    void revokeDeletesToken() {
        var token = new OAuthTokenRecord(
                ACTOR, TENANT, PROVIDER, "token", null,
                Set.of(), null, Instant.now());
        tokenStore.store(token);

        manager.revoke(ACTOR, PROVIDER, TENANT);

        assertThat(tokenStore.findByActorId(ACTOR, PROVIDER, TENANT)).isEmpty();
    }

    // --- stubs ---

    interface RefreshClient {
        OAuthTokenResponse refresh(String tokenEndpoint, String refreshToken,
                                    String clientId, String clientSecret);
    }

    private static class StubRefreshClient implements OAuthTokenManagerCore.TokenRefreshClient {
        OAuthTokenResponse nextResponse;
        String lastRefreshToken;
        boolean failOnRefresh;

        @Override
        public OAuthTokenResponse refresh(String refreshToken) {
            lastRefreshToken = refreshToken;
            if (failOnRefresh) {
                throw new OAuthException("Refresh failed");
            }
            return nextResponse;
        }
    }

    private static class InMemOAuthTokenStore implements OAuthTokenStore {
        final ConcurrentHashMap<String, OAuthTokenRecord> stored = new ConcurrentHashMap<>();

        @Override
        public void store(OAuthTokenRecord record) {
            stored.put(key(record.actorId(), record.provider(), record.tenancyId()), record);
        }

        @Override
        public Optional<OAuthTokenRecord> findByActorId(String actorId, String provider, String tenancyId) {
            return Optional.ofNullable(stored.get(key(actorId, provider, tenancyId)));
        }

        @Override
        public List<OAuthTokenRecord> findAllByActorId(String actorId, String tenancyId) {
            return stored.values().stream()
                    .filter(r -> r.actorId().equals(actorId) && r.tenancyId().equals(tenancyId))
                    .toList();
        }

        @Override
        public void delete(String actorId, String provider, String tenancyId) {
            stored.remove(key(actorId, provider, tenancyId));
        }

        @Override
        public void updateTokens(String actorId, String provider, String tenancyId,
                                  String accessToken, String refreshToken, Instant expiresAt) {
            stored.computeIfPresent(key(actorId, provider, tenancyId),
                    (k, old) -> new OAuthTokenRecord(actorId, tenancyId, provider,
                            accessToken, refreshToken, old.grantedScopes(), expiresAt, old.createdAt()));
        }

        private static String key(String a, String p, String t) { return a + ":" + p + ":" + t; }
    }
}
