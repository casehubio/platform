package io.casehub.platform.registration;

import java.time.Instant;

public record EmailVerificationToken(
    String tokenHash,
    String userId,
    String tenancyId,
    Instant expiresAt,
    Instant createdAt
) {}
