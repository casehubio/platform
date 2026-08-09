package io.casehub.platform.api.acl;

public enum WorkerAction {
    READ_CONTEXT(AclAction.READ),
    WRITE_CONTEXT(AclAction.WRITE),
    SIGNAL_CASE(AclAction.WRITE),
    READ_EVENT_LOG(AclAction.READ),
    READ_PLAN_ITEMS(AclAction.READ),
    SPAWN_SUB_CASE(AclAction.WRITE),
    CLAIM_WORK_ITEM(AclAction.CLAIM),
    ADMIN(AclAction.ADMIN);

    private final AclAction aclAction;

    WorkerAction(AclAction aclAction) {
        this.aclAction = aclAction;
    }

    public AclAction aclAction() {
        return aclAction;
    }
}
