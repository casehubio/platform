package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.ConflictException;
import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginEvent;
import io.casehub.plugin.catalog.PluginManifest;
import io.casehub.plugin.catalog.PluginPackage;
import io.casehub.plugin.catalog.PluginRequirements;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPluginCatalogTest {

    private final DefaultPluginCatalog catalog = new DefaultPluginCatalog();

    @Test
    void install_addsToInstalled() {
        catalog.install(pkg("monitor", "1.0.0", Set.of("monitoring")));

        assertThat(catalog.installed()).hasSize(1);
        assertThat(catalog.resolve("monitor")).isPresent();
    }

    @Test
    void install_firesAddedEvent() {
        List<PluginEvent> events = new CopyOnWriteArrayList<>();
        catalog.watch(events::add);

        catalog.install(pkg("monitor", "1.0.0", Set.of("monitoring")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PluginEvent.Added.class);
    }

    @Test
    void install_throwsOnConflict() {
        catalog.install(pkg("plugin-a", "1.0.0", Set.of("rest-request")));

        assertThatThrownBy(() ->
                catalog.install(pkg("plugin-b", "1.0.0", Set.of("rest-request"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void install_samePluginNewVersion_updatesAndFiresUpdated() {
        List<PluginEvent> events = new CopyOnWriteArrayList<>();
        catalog.install(pkg("monitor", "1.0.0", Set.of("monitoring")));
        catalog.watch(events::add);

        catalog.install(pkg("monitor", "2.0.0", Set.of("monitoring")));

        assertThat(catalog.installed()).hasSize(1);
        assertThat(catalog.resolve("monitor").get()
                .coordinates().version()).isEqualTo("2.0.0");
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PluginEvent.Updated.class);
    }

    @Test
    void uninstall_removesAndFiresEvent() {
        List<PluginEvent> events = new CopyOnWriteArrayList<>();
        catalog.install(pkg("monitor", "1.0.0", Set.of("monitoring")));
        catalog.watch(events::add);

        catalog.uninstall(new PluginCoordinates("monitor", "1.0.0"));

        assertThat(catalog.installed()).isEmpty();
        assertThat(catalog.resolve("monitor")).isEmpty();
        assertThat(events).hasSize(1);
        assertThat(events.get(0)).isInstanceOf(PluginEvent.Removed.class);
    }

    @Test
    void uninstall_unknownPlugin_isNoOp() {
        assertThatCode(() ->
                catalog.uninstall(new PluginCoordinates("unknown", "1.0.0")))
                .doesNotThrowAnyException();
    }

    @Test
    void resolve_returnsEmptyForUnknown() {
        assertThat(catalog.resolve("nonexistent")).isEmpty();
    }

    @Test
    void installed_returnsDefensiveCopy() {
        catalog.install(pkg("monitor", "1.0.0", Set.of("monitoring")));
        List<PluginPackage> first  = catalog.installed();
        List<PluginPackage> second = catalog.installed();
        assertThat(first).isEqualTo(second);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void multiplePlugins_independentLifecycle() {
        catalog.install(pkg("plugin-a", "1.0.0", Set.of("cap-a")));
        catalog.install(pkg("plugin-b", "1.0.0", Set.of("cap-b")));

        assertThat(catalog.installed()).hasSize(2);

        catalog.uninstall(new PluginCoordinates("plugin-a", "1.0.0"));

        assertThat(catalog.installed()).hasSize(1);
        assertThat(catalog.resolve("plugin-a")).isEmpty();
        assertThat(catalog.resolve("plugin-b")).isPresent();
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
