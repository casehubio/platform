package io.casehub.platform.registration.core;

import io.casehub.platform.api.authn.ChallengeResponse;

import java.time.Instant;

record PasswordChallengeResponse(String challengeId, Instant expiresAt) implements ChallengeResponse {
}
