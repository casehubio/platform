package io.casehub.platform.registration;

import java.time.Instant;
import java.util.Objects;

public record UserRecord(
    String id,
    String email,
    String displayName,
    String tenancyId,
    UserStatus status,
    String registrationMethod,
    Instant createdAt,
    Instant activatedAt
) {
    public UserRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(registrationMethod, "registrationMethod");
    }
}
