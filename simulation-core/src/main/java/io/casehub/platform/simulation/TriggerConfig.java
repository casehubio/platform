package io.casehub.platform.simulation;

public record TriggerConfig(String eventType, String condition) {

    public TriggerConfig {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("TriggerConfig requires a non-blank event-type");
        }
    }
}
