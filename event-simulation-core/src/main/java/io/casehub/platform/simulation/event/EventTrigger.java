package io.casehub.platform.simulation.event;

import java.util.Map;

public record EventTrigger(
        String eventType,
        String tenancyId,
        Map<String, Object> context) {

    public EventTrigger {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be null or blank");
        }
        if (tenancyId == null || tenancyId.isBlank()) {
            throw new IllegalArgumentException("tenancyId must not be null or blank");
        }
        context = context == null ? Map.of() : Map.copyOf(context);
    }
}
