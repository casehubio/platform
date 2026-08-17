package io.casehub.platform.api.acl;

public record WorkerAction(String name, AclAction aclAction) {

    public static final WorkerAction READ_CONTEXT = new WorkerAction("read-context", AclAction.READ);
    public static final WorkerAction SIGNAL_CASE = new WorkerAction("signal-case", AclAction.WRITE);

    public WorkerAction {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (aclAction == null) {
            throw new IllegalArgumentException("aclAction must not be null");
        }
    }
}
