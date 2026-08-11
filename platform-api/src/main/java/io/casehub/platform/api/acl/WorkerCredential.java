package io.casehub.platform.api.acl;

import java.time.Instant;
import java.util.Set;

public record WorkerCredential(
    String token,
    String actorId,
    ResourceId resourceId,
    String tenancyId,
    Set<WorkerAction> actions,
    Instant expiresAt,
    Instant createdAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
