package io.casehub.platform.api.model;

import java.util.Set;

public record ModelCatalogChangedEvent(
    String sourceId,
    Set<String> addedIds,
    Set<String> removedIds,
    Set<String> updatedIds
) {
    public ModelCatalogChangedEvent {
        addedIds = addedIds != null ? Set.copyOf(addedIds) : Set.of();
        removedIds = removedIds != null ? Set.copyOf(removedIds) : Set.of();
        updatedIds = updatedIds != null ? Set.copyOf(updatedIds) : Set.of();
    }

    public boolean hasChanges() {
        return !addedIds.isEmpty() || !removedIds.isEmpty() || !updatedIds.isEmpty();
    }
}
