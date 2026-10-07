package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Objects;

public record StaticCredentialRecord(
    String actorId,
    String tenancyId,
    String provider,
    String credential,
    CredentialType type,
    Instant createdAt,
    Instant updatedAt
) {
    public StaticCredentialRecord {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(credential, "credential");
        Objects.requireNonNull(type, "type");
    }
}
