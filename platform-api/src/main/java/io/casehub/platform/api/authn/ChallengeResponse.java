package io.casehub.platform.api.authn;

import java.time.Instant;

public interface ChallengeResponse {
    String challengeId();
    Instant expiresAt();
}
