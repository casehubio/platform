package io.casehub.platform.registration;

import java.time.Instant;

public record PasswordResetToken(
    String tokenHash,
    String userId,
    String tenancyId,
    Instant expiresAt,
    Instant createdAt
) {}
