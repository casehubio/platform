package io.casehub.platform.api.acl;

public class AccessDeniedException extends SecurityException {

    private final String actorId;
    private final String resourceId;
    private final AclAction action;

    public AccessDeniedException(String actorId, String resourceId, AclAction action) {
        super("Access denied: actor=" + actorId + " resource=" + resourceId + " action=" + action);
        this.actorId = actorId;
        this.resourceId = resourceId;
        this.action = action;
    }

    public String actorId() { return actorId; }
    public String resourceId() { return resourceId; }
    public AclAction action() { return action; }
}
