package io.casehub.yaml.test.engine;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YamlTestEngineTest {

    @Test
    void engineHasCorrectId() {
        var engine = new YamlTestEngine();
        assertThat(engine.getId()).isEqualTo("casehub-yaml");
    }

    @Test
    void engineDiscoversTestYamlFiles() {
        var engine = new YamlTestEngine();
        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectDirectory("src/test/resources/discovery-suite"))
                .build();

        var descriptor = engine.discover(request, UniqueId.forEngine("casehub-yaml"));
        assertThat(descriptor.getChildren()).isNotEmpty();
    }

    @Test
    void emptyDirectoryProducesNoChildren() {
        var engine = new YamlTestEngine();
        var empty = Path.of("src/test/resources/empty-suite");
        empty.toFile().mkdirs();

        try {
            var request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(DiscoverySelectors.selectDirectory(empty.toString()))
                    .build();
            var descriptor = engine.discover(request, UniqueId.forEngine("casehub-yaml"));
            assertThat(descriptor.getChildren()).isEmpty();
        } finally {
            empty.toFile().delete();
        }
    }
}
