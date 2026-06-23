package io.casehub.platform.api.identity;

public class MissingTenancyException extends RuntimeException {

    private final String actorId;

    public MissingTenancyException(String actorId) {
        super("No tenancy identifier for authenticated principal: " + actorId);
        this.actorId = actorId;
    }

    public String actorId() {
        return actorId;
    }
}
