package io.casehub.plugin.catalog;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record PluginPackage(
        PluginManifest manifest,
        Path directory,
        List<Path> definitionPaths,
        List<Path> scriptPaths) {

    public PluginPackage {
        Objects.requireNonNull(manifest, "manifest");
        Objects.requireNonNull(directory, "directory");
        if (definitionPaths == null) definitionPaths = List.of();
        else definitionPaths = List.copyOf(definitionPaths);
        if (scriptPaths == null) scriptPaths = List.of();
        else scriptPaths = List.copyOf(scriptPaths);
    }

    public PluginCoordinates coordinates() {
        return manifest.coordinates();
    }
}
