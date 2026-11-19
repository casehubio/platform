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
    Instant updatedAt,
    CredentialState state,
    Instant expiresAt
) {
    public StaticCredentialRecord {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(type, "type");
        if (state == null) state = CredentialState.ACTIVE;
    }

    public StaticCredentialRecord(String actorId, String tenancyId, String provider,
            String credential, CredentialType type, Instant createdAt, Instant updatedAt) {
        this(actorId, tenancyId, provider, credential, type, createdAt, updatedAt,
                CredentialState.ACTIVE, null);
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
