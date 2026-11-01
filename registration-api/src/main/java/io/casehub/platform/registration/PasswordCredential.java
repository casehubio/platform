package io.casehub.platform.registration;

import java.time.Instant;

public record PasswordCredential(
    String actorId,
    String tenancyId,
    String hashedPassword,
    Instant createdAt,
    Instant lastUsedAt
) {}
