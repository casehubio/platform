package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record ChallengeRecord(
    String challengeId,
    String method,
    String tenancyId,
    byte[] challengeData,
    Instant createdAt,
    Instant expiresAt
) {
    public ChallengeRecord {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(tenancyId, "tenancyId");
        challengeData = challengeData != null ? Arrays.copyOf(challengeData, challengeData.length) : null;
    }

    @Override
    public byte[] challengeData() {
        return challengeData != null ? Arrays.copyOf(challengeData, challengeData.length) : null;
    }
}
