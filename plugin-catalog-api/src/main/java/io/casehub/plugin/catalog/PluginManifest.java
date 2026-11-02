package io.casehub.plugin.catalog;

import java.util.Set;

public interface PluginManifest {

    PluginCoordinates coordinates();

    String description();

    PluginRequirements requirements();

    Set<String> provides();
}
