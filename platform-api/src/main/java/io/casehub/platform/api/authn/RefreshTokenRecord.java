package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record RefreshTokenRecord(
    String token,
    String familyId,
    String actorId,
    String tenancyId,
    Set<String> groups,
    String authMethod,
    Instant createdAt,
    Instant expiresAt,
    boolean consumed
) {
    public RefreshTokenRecord {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(authMethod, "authMethod");
        groups = groups != null ? Set.copyOf(groups) : Set.of();
    }
}
