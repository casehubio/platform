package io.casehub.platform.simulation.event;

public record EventSourceConfig(
        String qualifiedName,
        String eventType,
        String tenancyId) {

    public EventSourceConfig {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            throw new IllegalArgumentException("qualifiedName must not be null or blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        if (tenancyId == null || tenancyId.isBlank()) {
            throw new IllegalArgumentException("tenancyId must not be null or blank");
        }
    }
}
