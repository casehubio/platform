package io.casehub.platform.acl.admin;

import io.casehub.platform.api.acl.AclAction;

import java.time.Instant;

public record AclEntryInput(String actorId, String resourceId, AclAction action, Instant expiresAt) {
}
