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


    public boolean isEmpty() {
        return locations.isEmpty();
    }

    public static LocationIndex empty() {
        return EMPTY;
    }
}
