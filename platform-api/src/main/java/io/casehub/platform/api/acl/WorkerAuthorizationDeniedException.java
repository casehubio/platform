package io.casehub.platform.api.acl;

public class WorkerAuthorizationDeniedException extends SecurityException {

    private final String actorId;
    private final String caseDefinitionId;
    private final String reason;

    public WorkerAuthorizationDeniedException(String actorId, String caseDefinitionId, String reason) {
        super("Worker authorization denied: actor=" + actorId
              + " caseDefinition=" + caseDefinitionId + " reason=" + reason);
        this.actorId = actorId;
        this.caseDefinitionId = caseDefinitionId;
        this.reason = reason;
    }

    public String actorId() {
        return actorId;
    }

    public String caseDefinitionId() {
        return caseDefinitionId;
    }

    public String reason() {
        return reason;
    }
}
