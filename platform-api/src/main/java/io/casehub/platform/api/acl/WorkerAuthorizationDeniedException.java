package io.casehub.platform.api.acl;

public class WorkerAuthorizationDeniedException extends SecurityException {

    private final String actorId;
    private final String definitionId;
    private final String reason;

    public WorkerAuthorizationDeniedException(String actorId, String definitionId, String reason) {
        super("Worker authorization denied: actor=" + actorId
              + " definition=" + definitionId + " reason=" + reason);
        this.actorId = actorId;
        this.definitionId = definitionId;
        this.reason = reason;
    }

    public String actorId() {
        return actorId;
    }

    public String definitionId() {
        return definitionId;
    }

    public String reason() {
        return reason;
    }
}
