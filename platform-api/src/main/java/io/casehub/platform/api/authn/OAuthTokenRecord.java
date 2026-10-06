package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record OAuthTokenRecord(
    String actorId,
    String tenancyId,
    String provider,
    String accessToken,
    String refreshToken,
    Set<String> grantedScopes,
    Instant expiresAt,
    Instant createdAt
) {
    public OAuthTokenRecord {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        Objects.requireNonNull(provider, "provider");
        grantedScopes = grantedScopes != null ? Set.copyOf(grantedScopes) : Set.of();
    }
}
