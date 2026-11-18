package io.casehub.yaml.core.error;

import java.util.IdentityHashMap;

public final class LocationIndex {

    private static final LocationIndex EMPTY = new LocationIndex();

    private final IdentityHashMap<Object, SourceLocation> locations;

    public LocationIndex() {
        this.locations = new IdentityHashMap<>();
    }

    public void put(Object yamlObject, SourceLocation location) {
        if (this == EMPTY) {
            throw new UnsupportedOperationException("Cannot modify the empty LocationIndex");
        }
        locations.put(yamlObject, location);
    }

    public SourceLocation locationOf(Object yamlObject) {
        SourceLocation loc = locations.get(yamlObject);
        return loc != null ? loc : SourceLocation.UNKNOWN;
    }

    public SourceLocation locationOf(Object yamlObject, String fallbackFile) {
        SourceLocation loc = locations.get(yamlObject);
        if (loc != null) {return loc;}
        return fallbackFile != null ? new SourceLocation(fallbackFile, -1, -1) : SourceLocation.UNKNOWN;
    }


    public void transferFrom(LocationIndex sourceIndex, Object sourceObject, Object targetObject) {
        if (this == EMPTY) {
            throw new UnsupportedOperationException("Cannot modify the empty LocationIndex");
        }
        SourceLocation loc = sourceIndex.locations.get(sourceObject);
        if (loc != null) {
            locations.put(targetObject, loc);
        }
        if (sourceObject instanceof java.util.Map<?, ?> sourceMap
            && targetObject instanceof java.util.Map<?, ?> targetMap) {
            for (var key : sourceMap.keySet()) {
                Object sv = sourceMap.get(key);
                Object tv = targetMap.get(key);
                if (sv != null && tv != null) {
                    transferFrom(sourceIndex, sv, tv);
                }
            }
        } else if (sourceObject instanceof java.util.List<?> sourceList
                   && targetObject instanceof java.util.List<?> targetList) {
            int size = Math.min(sourceList.size(), targetList.size());
            for (int i = 0; i < size; i++) {
                transferFrom(sourceIndex, sourceList.get(i), targetList.get(i));
            }
        }
    }

    public boolean isEmpty() {
        return locations.isEmpty();
    }

    public static LocationIndex empty() {
        return EMPTY;
    }
}
