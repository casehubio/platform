package io.casehub.platform.registration;

import java.time.Instant;

public record PasswordResetRequested(String userId, String tenancyId, Instant requestedAt) {}
