package io.casehub.platform.api.acl;

public interface WorkerAuthorizationPolicy {

    default AuthorizationDecision evaluate(WorkerPermissionRequest request) {
        return AuthorizationDecision.approve();
    }
}
