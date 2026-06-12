package io.casehub.platform.api.acl;

import java.time.Instant;

public record AclEntry(
    String actorId,
    String resourceId,
    AclAction action,
    Instant grantedAt,
    Instant expiresAt,
    String tenancyId
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
