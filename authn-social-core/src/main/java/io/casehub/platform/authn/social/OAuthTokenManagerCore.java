package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.AuthenticationEventListener;
import io.casehub.platform.api.authn.OAuthTokenRefreshFailed;
import io.casehub.platform.api.authn.OAuthTokenRecord;
import io.casehub.platform.api.authn.OAuthTokenStore;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class OAuthTokenManagerCore {

    private final OAuthTokenStore             tokenStore;
    private final TokenRefreshClient          refreshClient;
    private final AuthenticationEventListener eventListener;

    public OAuthTokenManagerCore(OAuthTokenStore tokenStore, TokenRefreshClient refreshClient,
                                 AuthenticationEventListener eventListener) {
        this.tokenStore    = Objects.requireNonNull(tokenStore, "tokenStore");
        this.refreshClient = Objects.requireNonNull(refreshClient, "refreshClient");
        this.eventListener = Objects.requireNonNull(eventListener, "eventListener");
    }

    public Optional<OAuthTokenRecord> getValidToken(String actorId, String provider, String tenancyId) {
        var existing = tokenStore.findByActorId(actorId, provider, tenancyId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        var token = existing.get();
        if (token.expiresAt() == null || token.expiresAt().isAfter(Instant.now())) {
            return Optional.of(token);
        }

        if (token.refreshToken() == null) {
            return Optional.empty();
        }

        try {
            var refreshed = refreshClient.refresh(provider, token.refreshToken());
            Instant newExpiry = refreshed.expiresIn() > 0
                                ? Instant.now().plusSeconds(refreshed.expiresIn())
                                : null;
            String newRefreshToken = refreshed.refreshToken() != null
                                     ? refreshed.refreshToken()
                                     : token.refreshToken();

            tokenStore.updateTokens(actorId, provider, tenancyId,
                                    refreshed.accessToken(), newRefreshToken, newExpiry);

            return tokenStore.findByActorId(actorId, provider, tenancyId);
        } catch (Exception e) {
            try {
                eventListener.onOAuthTokenRefreshFailed(new OAuthTokenRefreshFailed(
                        actorId, tenancyId, provider, e.getMessage()));
            } catch (Exception listenerEx) {
                java.util.logging.Logger.getLogger(OAuthTokenManagerCore.class.getName())
                        .log(java.util.logging.Level.WARNING, "Authentication event listener failed", listenerEx);
            }
            return Optional.empty();
        }
    }

    public void revoke(String actorId, String provider, String tenancyId) {
        tokenStore.delete(actorId, provider, tenancyId);
    }

    public interface TokenRefreshClient {
        OAuthTokenResponse refresh(String provider, String refreshToken);
    }
}
