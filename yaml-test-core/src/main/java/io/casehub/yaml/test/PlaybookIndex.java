package io.casehub.yaml.test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class PlaybookIndex {

    private static final String SUFFIX = ".playbook.yaml";
    private static final String CLASSPATH_ROOT = "playbooks";

    private final Map<String, Path> index;

    private PlaybookIndex(Map<String, Path> index) {
        this.index = Collections.unmodifiableMap(index);
    }

    public static PlaybookIndex fromClasspath() {
        return fromClasspath(Thread.currentThread().getContextClassLoader());
    }

    public static PlaybookIndex fromClasspath(ClassLoader classLoader) {
        Map<String, Path> index = new LinkedHashMap<>();
        try {
            var resources = classLoader.getResources(CLASSPATH_ROOT);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                try {
                    Path root = Path.of(url.toURI());
                    if (Files.isDirectory(root)) {
                        scanDirectory(root, root, index);
                    }
                } catch (URISyntaxException | IllegalArgumentException e) {
                    // jar: URLs or non-file URLs — skip
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan classpath for playbooks", e);
        }
        return new PlaybookIndex(index);
    }

    public static PlaybookIndex fromDirectory(Path root) {
        Map<String, Path> index = new LinkedHashMap<>();
        if (Files.isDirectory(root)) {
            scanDirectory(root, root, index);
        }
        return new PlaybookIndex(index);
    }

    public Optional<Path> resolve(String qualifiedId) {
        return Optional.ofNullable(index.get(qualifiedId));
    }

    public Map<String, Path> entries() {
        return index;
    }

    public int size() {
        return index.size();
    }

    private static void scanDirectory(Path root, Path dir, Map<String, Path> index) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(SUFFIX))
                .forEach(p -> {
                    String relative = root.relativize(p).toString();
                    String id = relative.substring(0, relative.length() - SUFFIX.length());
                    id = id.replace('\\', '/');
                    if (index.containsKey(id)) {
                        throw new IllegalStateException(
                                "Duplicate playbook ID '" + id + "': " + index.get(id) + " and " + p);
                    }
                    index.put(id, p);
                });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan playbook directory: " + dir, e);
        }
    }
}
