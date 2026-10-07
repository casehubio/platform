package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.OAuthTokenRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTokenManagerCoreTest {

    private static final String ACTOR = "user-1";
    private static final String TENANT = "tenant-1";
    private static final String PROVIDER = "google";

    private OAuthTestFixtures.InMemOAuthTokenStore tokenStore;
    private StubRefreshClient refreshClient;
    private OAuthTokenManagerCore manager;

    @BeforeEach
    void setUp() {
        tokenStore = new OAuthTestFixtures.InMemOAuthTokenStore();
        refreshClient = new StubRefreshClient();
        manager = new OAuthTokenManagerCore(tokenStore, refreshClient, OAuthTestFixtures.NO_OP_LISTENER);
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

    @Test
    void refreshFailureFiresOAuthTokenRefreshFailedEvent() {
        var capturing = new CapturingEventListener();
        var mgr       = new OAuthTokenManagerCore(tokenStore, refreshClient, capturing);

        var expired = new OAuthTokenRecord(
                ACTOR, TENANT, PROVIDER, "old-token", "refresh-token",
                Set.of("openid"), Instant.now().minusSeconds(60), Instant.now());
        tokenStore.store(expired);

        refreshClient.failOnRefresh = true;
        mgr.getValidToken(ACTOR, PROVIDER, TENANT);

        assertThat(capturing.lastEvent).isNotNull();
        assertThat(capturing.lastEvent.actorId()).isEqualTo(ACTOR);
        assertThat(capturing.lastEvent.tenancyId()).isEqualTo(TENANT);
        assertThat(capturing.lastEvent.provider()).isEqualTo(PROVIDER);
        assertThat(capturing.lastEvent.reason()).contains("Refresh failed");
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

    private static class CapturingEventListener implements io.casehub.platform.api.authn.AuthenticationEventListener {
        io.casehub.platform.api.authn.OAuthTokenRefreshFailed lastEvent;

        @Override
        public void onOAuthTokenRefreshFailed(io.casehub.platform.api.authn.OAuthTokenRefreshFailed event) {
            this.lastEvent = event;
        }
    }
}
