package io.casehub.platform.api.registry;

import java.util.Objects;

public record CascadeEffect(
        String targetId,
        CascadeAction action,
        Relationship relationship,
        RegistryEntry targetEntry,
        String failure
) {
    public CascadeEffect {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(relationship, "relationship");
    }
}
