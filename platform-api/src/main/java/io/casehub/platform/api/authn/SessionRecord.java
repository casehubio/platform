package io.casehub.platform.api.authn;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record SessionRecord(
    String sessionId,
    String actorId,
    String tenancyId,
    Set<String> groups,
    String authMethod,
    String csrfToken,
    Instant createdAt,
    Instant expiresAt,
    String deviceFingerprint
) {
    public SessionRecord {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(tenancyId, "tenancyId");
        groups = groups != null ? Set.copyOf(groups) : Set.of();
    }
}
