package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginManifest;
import io.casehub.plugin.catalog.PluginRequirements;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

public final class ManifestParser {

    private ManifestParser() {}

    public static PluginManifest parse(Path manifestFile,
                                        PluginCoordinates coordinates) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(manifestFile)) {
            props.load(in);
        }

        String description = props.getProperty("description", "");
        Set<String> provides = parseSet(props.getProperty("provides", ""));

        String runtimeVersionRange = props.getProperty("requires.runtime-version");
        List<String> primitives = parseList(props.getProperty("requires.primitives", ""));
        List<String> types = parseList(props.getProperty("requires.types", ""));

        PluginRequirements requirements = new PluginRequirements(
                Optional.ofNullable(runtimeVersionRange),
                primitives, types);

        return new ParsedManifest(coordinates, description, requirements, provides);
    }

    private static Set<String> parseSet(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Set.copyOf(Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    private static List<String> parseList(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private record ParsedManifest(
            PluginCoordinates coordinates,
            String description,
            PluginRequirements requirements,
            Set<String> provides) implements PluginManifest {}
}
