package io.casehub.plugin.catalog;

import java.util.List;
import java.util.Optional;

public record PluginRequirements(
        Optional<String> runtimeVersionRange,
        List<String> primitives,
        List<String> types) {

    public static final PluginRequirements NONE =
            new PluginRequirements(Optional.empty(), List.of(), List.of());

    public PluginRequirements {
        if (runtimeVersionRange == null) runtimeVersionRange = Optional.empty();
        if (primitives == null) primitives = List.of();
        else primitives = List.copyOf(primitives);
        if (types == null) types = List.of();
        else types = List.copyOf(types);
    }
}
