package io.casehub.plugin.catalog;

import java.util.Objects;

public record PluginCoordinates(String name, String version) {

    public PluginCoordinates {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        Objects.requireNonNull(version, "version");
        if (version.isBlank()) throw new IllegalArgumentException("version must not be blank");
    }

    @Override
    public String toString() {
        return name + ":" + version;
    }

    public static PluginCoordinates parse(String coordString) {
        int idx = coordString.indexOf(':');
        if (idx < 0) throw new IllegalArgumentException(
                "Invalid coordinates format, expected 'name:version': " + coordString);
        return new PluginCoordinates(
                coordString.substring(0, idx),
                coordString.substring(idx + 1));
    }
}
