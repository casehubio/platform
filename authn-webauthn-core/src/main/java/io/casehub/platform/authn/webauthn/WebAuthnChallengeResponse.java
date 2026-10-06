package io.casehub.platform.authn.webauthn;

import io.casehub.platform.api.authn.ChallengeResponse;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class WebAuthnChallengeResponse implements ChallengeResponse {

    private final String challengeId;
    private final Instant expiresAt;
    private final Map<String, Object> options;

    public WebAuthnChallengeResponse(String challengeId, Instant expiresAt, Map<String, Object> options) {
        this.challengeId = Objects.requireNonNull(challengeId, "challengeId");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.options = Map.copyOf(Objects.requireNonNull(options, "options"));
    }

    @Override
    public String challengeId() {
        return challengeId;
    }

    @Override
    public Instant expiresAt() {
        return expiresAt;
    }

    public Map<String, Object> options() {
        return options;
    }
}
