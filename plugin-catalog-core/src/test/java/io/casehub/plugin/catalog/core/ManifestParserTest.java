package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.PluginCoordinates;
import io.casehub.plugin.catalog.PluginManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManifestParserTest {

    @TempDir Path tempDir;

    @Test
    void parse_readsAllFields() throws IOException {
        Path manifest = writeManifest(
                "description=Monitor endpoint health\n" +
                "provides=monitoring,health-check\n" +
                "requires.runtime-version=>=1.0.0\n" +
                "requires.primitives=rest-request,signal\n" +
                "requires.types=endpoint\n");

        PluginManifest result = ManifestParser.parse(manifest,
                new PluginCoordinates("monitor", "2.0.0"));

        assertThat(result.coordinates().name()).isEqualTo("monitor");
        assertThat(result.coordinates().version()).isEqualTo("2.0.0");
        assertThat(result.description()).isEqualTo("Monitor endpoint health");
        assertThat(result.provides()).containsExactlyInAnyOrder("monitoring", "health-check");
        assertThat(result.requirements().runtimeVersionRange()).contains(">=1.0.0");
        assertThat(result.requirements().primitives()).containsExactly("rest-request", "signal");
        assertThat(result.requirements().types()).containsExactly("endpoint");
    }

    @Test
    void parse_handlesMinimalManifest() throws IOException {
        Path manifest = writeManifest("");

        PluginManifest result = ManifestParser.parse(manifest,
                new PluginCoordinates("bare", "1.0.0"));

        assertThat(result.description()).isEmpty();
        assertThat(result.provides()).isEmpty();
        assertThat(result.requirements().runtimeVersionRange()).isEmpty();
        assertThat(result.requirements().primitives()).isEmpty();
        assertThat(result.requirements().types()).isEmpty();
    }

    @Test
    void parse_trimsWhitespaceInCsvValues() throws IOException {
        Path manifest = writeManifest("provides= cap-a , cap-b , cap-c \n");

        PluginManifest result = ManifestParser.parse(manifest,
                new PluginCoordinates("trimmed", "1.0.0"));

        assertThat(result.provides()).containsExactlyInAnyOrder("cap-a", "cap-b", "cap-c");
    }

    @Test
    void parse_throwsOnMissingFile() {
        Path missing = tempDir.resolve("nonexistent.properties");

        assertThatThrownBy(() -> ManifestParser.parse(missing,
                new PluginCoordinates("missing", "1.0.0")))
                .isInstanceOf(IOException.class);
    }

    private Path writeManifest(String content) throws IOException {
        Path file = tempDir.resolve("plugin.properties");
        Files.writeString(file, content);
        return file;
    }
}
