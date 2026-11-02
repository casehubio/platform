package io.casehub.plugin.catalog;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PluginEventTest {

    private static PluginPackage testPackage(String name, String version) {
        return new PluginPackage(
                new TestManifest(name, version),
                Path.of("/plugins/" + name + "/" + version),
                List.of(), List.of());
    }

    @Test
    void added_exposesPackage() {
        var pkg = testPackage("monitor", "1.0.0");
        var event = new PluginEvent.Added(pkg);
        assertThat(event.pluginPackage()).isSameAs(pkg);
    }

    @Test
    void updated_exposesBothVersions() {
        var old = testPackage("monitor", "1.0.0");
        var current = testPackage("monitor", "1.1.0");
        var event = new PluginEvent.Updated(current, old);
        assertThat(event.pluginPackage().coordinates().version()).isEqualTo("1.1.0");
        assertThat(event.previous().coordinates().version()).isEqualTo("1.0.0");
    }

    @Test
    void sealedHierarchy_patternMatch() {
        PluginEvent event = new PluginEvent.Removed(testPackage("monitor", "1.0.0"));
        String result = switch (event) {
            case PluginEvent.Added a -> "added";
            case PluginEvent.Removed r -> "removed";
            case PluginEvent.Updated u -> "updated";
        };
        assertThat(result).isEqualTo("removed");
    }

    @Test
    void packageRecord_coordinatesDelegate() {
        var pkg = testPackage("monitor", "2.0.0");
        assertThat(pkg.coordinates().name()).isEqualTo("monitor");
        assertThat(pkg.coordinates().version()).isEqualTo("2.0.0");
    }

    @Test
    void packageRecord_rejectsNullManifest() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                                                   new PluginPackage(null, Path.of("/dir"), List.of(), List.of()))
                                       .isInstanceOf(NullPointerException.class);
    }

    @Test
    void packageRecord_rejectsNullDirectory() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                                                   new PluginPackage(new TestManifest("x", "1.0.0"), null, List.of(), List.of()))
                                       .isInstanceOf(NullPointerException.class);
    }

    @Test
    void packageRecord_defensiveCopyOfPaths() {
        var mutableDefs = new java.util.ArrayList<>(List.of(Path.of("/a.yaml")));
        var pkg = new PluginPackage(
                new TestManifest("x", "1.0.0"), Path.of("/dir"),
                mutableDefs, List.of());
        mutableDefs.add(Path.of("/should-not-appear.yaml"));
        assertThat(pkg.definitionPaths()).containsExactly(Path.of("/a.yaml"));
    }


    private record TestManifest(String name, String version) implements PluginManifest {
        @Override
        public PluginCoordinates coordinates() {
            return new PluginCoordinates(name, version);
        }

        @Override
        public String description() { return name + " plugin"; }

        @Override
        public PluginRequirements requirements() { return PluginRequirements.NONE; }

        @Override
        public Set<String> provides() { return Set.of(name); }
    }
}
