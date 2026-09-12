package io.casehub.platform.api.model;

import java.util.List;
import java.util.Set;

public interface MutableModelRegistry extends ModelRegistry {

    record CatalogDelta(Set<String> addedIds, Set<String> removedIds, Set<String> updatedIds) {
        public boolean hasChanges() {
            return !addedIds.isEmpty() || !removedIds.isEmpty() || !updatedIds.isEmpty();
        }
    }

    CatalogDelta replaceSource(String sourceId, int priority, List<ModelDescriptor> models);
}
