package io.casehub.platform.authn.social;

import io.casehub.platform.api.authn.ChallengeResponse;

import java.time.Instant;
import java.util.Objects;

public final class OAuthChallengeResponse implements ChallengeResponse {

    private final String challengeId;
    private final Instant expiresAt;
    private final String authorizationUrl;

    public OAuthChallengeResponse(String challengeId, Instant expiresAt, String authorizationUrl) {
        this.challengeId = Objects.requireNonNull(challengeId, "challengeId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.authorizationUrl = Objects.requireNonNull(authorizationUrl, "authorizationUrl");
    }

    @Override
    public String challengeId() {
        return challengeId;
    }

    @Override
    public Instant expiresAt() {
        return expiresAt;
    }

    public String authorizationUrl() {
        return authorizationUrl;
    }
}
