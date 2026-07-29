package io.casehub.platform.api.acl;

import java.time.Instant;

public record GrantRequest(String actorId, String resourceId, AclAction action, Instant expiresAt) {
}
