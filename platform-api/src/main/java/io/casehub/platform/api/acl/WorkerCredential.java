package io.casehub.platform.api.acl;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record WorkerCredential(
    String token,
    String actorId,
    UUID caseId,
    String tenancyId,
    Set<WorkerAction> actions,
    Instant expiresAt,
    Instant createdAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
