package io.casehub.platform.registration;

import java.time.Instant;

public record PasswordResetCompleted(String userId, String tenancyId, Instant completedAt) {}
