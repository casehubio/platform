package io.casehub.platform.api.subscription;

import java.util.List;
import java.util.Objects;

/**
 * Describes a discoverable event type that domain bridges register at startup.
 *
 * @param eventType   fully qualified event type identifier (e.g., "io.casehub.work.workitem.completed")
 * @param displayName human-readable label (e.g., "Work Item Completed")
 * @param description optional human-readable description (nullable)
 * @param fields      available POJO fields for constraint and target configuration
 */
public record EventTypeDescriptor(
        String eventType,
        String displayName,
        String description,
        List<EventFieldDescriptor> fields
) {
    public EventTypeDescriptor {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(fields, "fields");
        fields = List.copyOf(fields);
    }
}
