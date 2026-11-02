package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginEvent;
import io.casehub.plugin.catalog.PluginManifest;
import io.casehub.plugin.catalog.PluginPackage;
import io.casehub.plugin.catalog.PluginRepository;
import io.casehub.plugin.catalog.PluginRequirements;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryWatcher;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class FilesystemPluginRepository implements PluginRepository {

    private static final Logger LOG = Logger.getLogger(
            FilesystemPluginRepository.class.getName());
    private static final String MANIFEST_FILE = "plugin.properties";

    private final Path rootDir;
    private final CopyOnWriteArrayList<Consumer<PluginEvent>> listeners =
            new CopyOnWriteArrayList<>();
    private volatile DirectoryWatcher watcher;

    public FilesystemPluginRepository(Path rootDir) {
        this.rootDir = Objects.requireNonNull(rootDir, "rootDir");
    }

    @Override
    public List<PluginPackage> discover() {
        if (!Files.isDirectory(rootDir)) return List.of();

        List<PluginPackage> packages = new ArrayList<>();
        try (Stream<Path> names = Files.list(rootDir)) {
            names.filter(Files::isDirectory).forEach(nameDir -> {
                try (Stream<Path> versions = Files.list(nameDir)) {
                    versions.filter(Files::isDirectory).forEach(versionDir -> {
                        Path manifest = versionDir.resolve(MANIFEST_FILE);
                        if (Files.exists(manifest)) {
                            try {
                                packages.add(loadPackage(nameDir, versionDir));
                            } catch (IOException e) {
                                LOG.log(Level.WARNING,
                                        "Failed to load plugin from " + versionDir, e);
                            }
                        }
                    });
                } catch (IOException e) {
                    LOG.log(Level.WARNING,
                            "Failed to list versions in " + nameDir, e);
                }
            });
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to list plugins in " + rootDir, e);
        }
        return List.copyOf(packages);
    }

    @Override
    public Optional<PluginPackage> fetch(PluginCoordinates coordinates) {
        Path versionDir = rootDir.resolve(coordinates.name())
                .resolve(coordinates.version());
        Path manifest = versionDir.resolve(MANIFEST_FILE);
        if (!Files.exists(manifest)) return Optional.empty();
        try {
            return Optional.of(
                    loadPackage(rootDir.resolve(coordinates.name()), versionDir));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load plugin " + coordinates, e);
            return Optional.empty();
        }
    }

    @Override
    public void watch(Consumer<PluginEvent> listener) {
        listeners.add(listener);
        if (watcher == null) {
            startWatcher();
        }
    }

    @Override
    public void stopWatching() {
        DirectoryWatcher w = watcher;
        if (w != null) {
            try {
                w.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to close directory watcher", e);
            }
            watcher = null;
        }
    }

    private void startWatcher() {
        try {
            if (!Files.isDirectory(rootDir)) {
                Files.createDirectories(rootDir);
            }
            watcher = DirectoryWatcher.builder()
                    .path(rootDir)
                    .listener(event -> {
                        Path changed = event.path();
                        if (changed.getFileName().toString().equals(MANIFEST_FILE)) {
                            handleManifestChange(event.eventType(), changed);
                        }
                    })
                    .build();
            watcher.watchAsync();
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to start watching " + rootDir, e);
        }
    }

    private void handleManifestChange(
            DirectoryChangeEvent.EventType eventType,
            Path manifestPath) {
        try {
            Path versionDir = manifestPath.getParent();
            Path nameDir = versionDir.getParent();

            switch (eventType) {
                case CREATE -> {
                    PluginPackage pkg = loadPackage(nameDir, versionDir);
                    fire(new PluginEvent.Added(pkg));
                }
                case MODIFY -> {
                    PluginPackage pkg = loadPackage(nameDir, versionDir);
                    fire(new PluginEvent.Updated(pkg, null));
                }
                case DELETE -> {
                    String name = nameDir.getFileName().toString();
                    String version = versionDir.getFileName().toString();
                    PluginCoordinates coords = new PluginCoordinates(name, version);
                    PluginPackage stub = new PluginPackage(
                            new StubManifest(coords), versionDir, List.of(), List.of());
                    fire(new PluginEvent.Removed(stub));
                }
                default -> {}
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "Failed to handle manifest change at " + manifestPath, e);
        }
    }

    private PluginPackage loadPackage(Path nameDir, Path versionDir)
            throws IOException {
        String name = nameDir.getFileName().toString();
        String version = versionDir.getFileName().toString();
        PluginCoordinates coords = new PluginCoordinates(name, version);

        PluginManifest manifest = ManifestParser.parse(
                versionDir.resolve(MANIFEST_FILE), coords);

        List<Path> definitionPaths = new ArrayList<>();
        List<Path> scriptPaths = new ArrayList<>();
        try (Stream<Path> files = Files.list(versionDir)) {
            files.forEach(f -> {
                String fname = f.getFileName().toString();
                if (fname.endsWith(".yaml") || fname.endsWith(".yml")) {
                    definitionPaths.add(f);
                } else if (fname.endsWith(".py") || fname.endsWith(".js")) {
                    scriptPaths.add(f);
                }
            });
        }

        return new PluginPackage(manifest, versionDir,
                List.copyOf(definitionPaths), List.copyOf(scriptPaths));
    }

    private void fire(PluginEvent event) {
        for (Consumer<PluginEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    private record StubManifest(PluginCoordinates coordinates)
            implements PluginManifest {
        @Override public String description() { return ""; }
        @Override public PluginRequirements requirements() {
            return PluginRequirements.NONE;
        }
        @Override public Set<String> provides() { return Set.of(); }
    }
}
