package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginEvent;
import io.casehub.plugin.catalog.PluginPackage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FilesystemPluginRepositoryTest {

    @TempDir Path pluginsDir;

    @Test
    void discover_findsPluginsByConvention() throws IOException {
        createPlugin("monitoring-endpoint", "1.0.0");

        var repo = new FilesystemPluginRepository(pluginsDir);
        List<PluginPackage> packages = repo.discover();

        assertThat(packages).hasSize(1);
        assertThat(packages.get(0).coordinates().name())
                .isEqualTo("monitoring-endpoint");
        assertThat(packages.get(0).coordinates().version())
                .isEqualTo("1.0.0");
    }

    @Test
    void discover_multipleVersions() throws IOException {
        createPlugin("monitor", "1.0.0");
        createPlugin("monitor", "2.0.0");

        var repo = new FilesystemPluginRepository(pluginsDir);
        List<PluginPackage> packages = repo.discover();

        assertThat(packages).hasSize(2);
    }

    @Test
    void discover_skipsDirectoriesWithoutManifest() throws IOException {
        Files.createDirectories(pluginsDir.resolve("broken/1.0.0"));

        var repo = new FilesystemPluginRepository(pluginsDir);
        assertThat(repo.discover()).isEmpty();
    }

    @Test
    void fetch_returnsByCoordinates() throws IOException {
        createPlugin("monitor", "1.0.0");
        createPlugin("monitor", "2.0.0");

        var repo = new FilesystemPluginRepository(pluginsDir);
        var result = repo.fetch(new PluginCoordinates("monitor", "2.0.0"));

        assertThat(result).isPresent();
        assertThat(result.get().coordinates().version()).isEqualTo("2.0.0");
    }

    @Test
    void fetch_returnsEmptyForUnknown() {
        var repo = new FilesystemPluginRepository(pluginsDir);
        assertThat(repo.fetch(new PluginCoordinates("unknown", "1.0.0")))
                .isEmpty();
    }

    @Test
    void discover_collectsYamlDefinitionPaths() throws IOException {
        createPlugin("monitor", "1.0.0");
        Path dir = pluginsDir.resolve("monitor/1.0.0");
        Files.writeString(dir.resolve("steps.yaml"), "actions: {}");

        var repo = new FilesystemPluginRepository(pluginsDir);
        var packages = repo.discover();

        assertThat(packages.get(0).definitionPaths()).hasSize(1);
        assertThat(packages.get(0).definitionPaths().get(0).getFileName().toString())
                .isEqualTo("steps.yaml");
    }

    @Test
    void watch_firesAddedOnNewPlugin() throws Exception {
        var repo = new FilesystemPluginRepository(pluginsDir);
        List<PluginEvent> events = new CopyOnWriteArrayList<>();
        repo.watch(events::add);

        createPlugin("monitor", "1.0.0");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        assertThat(events).anyMatch(e -> e instanceof PluginEvent.Added));

        repo.stopWatching();
    }

    @Test
    void discover_returnsEmptyForNonExistentRoot() {
        var repo = new FilesystemPluginRepository(pluginsDir.resolve("nonexistent"));
        assertThat(repo.discover()).isEmpty();
    }

    @Test
    void discover_parsesManifestProvides() throws IOException {
        Path dir = pluginsDir.resolve("multi-cap").resolve("1.0.0");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("plugin.properties"),
                          "description=Multi capability plugin\n" +
                          "provides=rest-request,graphql-query\n" +
                          "requires.primitives=signal\n");

        var                 repo     = new FilesystemPluginRepository(pluginsDir);
        List<PluginPackage> packages = repo.discover();

        assertThat(packages).hasSize(1);
        assertThat(packages.get(0).manifest().provides())
                .containsExactlyInAnyOrder("rest-request", "graphql-query");
        assertThat(packages.get(0).manifest().requirements().primitives())
                .containsExactly("signal");
        assertThat(packages.get(0).manifest().description())
                .isEqualTo("Multi capability plugin");
    }

    @Test
    void discover_collectsScriptPaths() throws IOException {
        createPlugin("monitor", "1.0.0");
        Path dir = pluginsDir.resolve("monitor/1.0.0");
        Files.writeString(dir.resolve("handler.py"), "print('hello')");
        Files.writeString(dir.resolve("transform.js"), "module.exports = {}");

        var repo     = new FilesystemPluginRepository(pluginsDir);
        var packages = repo.discover();

        assertThat(packages.get(0).scriptPaths()).hasSize(2);
    }


    private void createPlugin(String name, String version) throws IOException {
        Path dir = pluginsDir.resolve(name).resolve(version);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("plugin.properties"),
                "description=" + name + " plugin\n" +
                "provides=" + name + "\n");
    }
}
