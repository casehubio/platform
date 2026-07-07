package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * Describes a field available on event POJOs for a given event type.
 *
 * @param name        property name on the event POJO (e.g., "assigneeId")
 * @param displayName human-readable label (e.g., "Assignee")
 * @param type        type hint for UI rendering (e.g., "string", "int", "enum")
 */
public record EventFieldDescriptor(
        String name,
        String displayName,
        String type
) {
    public EventFieldDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(type, "type");
    }
}
