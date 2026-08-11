package io.casehub.platform.api.acl;

public record WorkerAction(String name, AclAction aclAction) {

    public WorkerAction {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (aclAction == null) {
            throw new IllegalArgumentException("aclAction must not be null");
        }
    }
}
