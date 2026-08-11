package io.casehub.platform.api.acl;

import java.util.Set;

public record WorkerPermissionRequest(
    String actorId,
    String resourceType,
    Set<WorkerAction> actions,
    WorkerAuthorizationContext context,
    String tenancyId) {
}
