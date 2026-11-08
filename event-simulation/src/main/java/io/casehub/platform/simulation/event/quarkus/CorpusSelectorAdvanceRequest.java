package io.casehub.platform.simulation.event.quarkus;

public record CorpusSelectorAdvanceRequest(String qualifiedName, String trigger) {

    public void validate() {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            throw new IllegalArgumentException("qualifiedName is required");
        }
        if (trigger == null || trigger.isBlank()) {
            throw new IllegalArgumentException("trigger is required");
        }
    }
}
