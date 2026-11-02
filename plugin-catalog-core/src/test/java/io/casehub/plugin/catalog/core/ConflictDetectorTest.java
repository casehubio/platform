package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.ConflictException;
import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginManifest;
import io.casehub.plugin.catalog.PluginPackage;
import io.casehub.plugin.catalog.PluginRequirements;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConflictDetectorTest {

    private final ConflictDetector detector = new ConflictDetector();

    @Test
    void noConflict_whenProvidesAreDisjoint() {
        var existing = pkg("plugin-a", "1.0.0", Set.of("rest-request"));
        var incoming = pkg("plugin-b", "1.0.0", Set.of("graphql-query"));

        assertThatCode(() -> detector.check(List.of(existing), incoming))
                .doesNotThrowAnyException();
    }

    @Test
    void conflict_whenProvidesOverlap() {
        var existing = pkg("plugin-a", "1.0.0", Set.of("rest-request"));
        var incoming = pkg("plugin-b", "1.0.0", Set.of("rest-request", "graphql-query"));

        assertThatThrownBy(() -> detector.check(List.of(existing), incoming))
                .isInstanceOf(ConflictException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ConflictException.class))
                .satisfies(ce -> {
                    assertThat(ce.capability()).isEqualTo("rest-request");
                    assertThat(ce.existing().name()).isEqualTo("plugin-a");
                    assertThat(ce.incoming().name()).isEqualTo("plugin-b");
                });
    }

    @Test
    void noConflict_whenExistingListIsEmpty() {
        var incoming = pkg("plugin-a", "1.0.0", Set.of("rest-request"));
        assertThatCode(() -> detector.check(List.of(), incoming))
                .doesNotThrowAnyException();
    }

    @Test
    void samePlugin_differentVersion_noConflict() {
        var existing = pkg("plugin-a", "1.0.0", Set.of("rest-request"));
        var incoming = pkg("plugin-a", "2.0.0", Set.of("rest-request"));

        assertThatCode(() -> detector.check(List.of(existing), incoming))
                .doesNotThrowAnyException();
    }

    private static PluginPackage pkg(String name, String version, Set<String> provides) {
        return new PluginPackage(
                new TestManifest(name, version, provides),
                Path.of("/plugins/" + name), List.of(), List.of());
    }

    private record TestManifest(String name, String version,
                                Set<String> provides) implements PluginManifest {
        @Override public PluginCoordinates coordinates() {
            return new PluginCoordinates(name, version);
        }
        @Override public String description() { return name; }
        @Override public PluginRequirements requirements() {
            return PluginRequirements.NONE;
        }
    }
}
