package io.casehub.yaml.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybookIndexTest {

    @TempDir Path tempDir;

    @Test
    void scansDirectoryAndStripsPlaybookSuffix() throws IOException {
        Path fc = tempDir.resolve("flow-control");
        Files.createDirectories(fc);
        Files.writeString(fc.resolve("sequential.playbook.yaml"), "playbook: '1.0'\n");
        Files.writeString(fc.resolve("retry.playbook.yaml"), "playbook: '1.0'\n");

        var index = PlaybookIndex.fromDirectory(tempDir);
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.resolve("flow-control/sequential")).isPresent();
        assertThat(index.resolve("flow-control/retry")).isPresent();
    }

    @Test
    void resolvesNestedDirectories() throws IOException {
        Path deep = tempDir.resolve("a/b");
        Files.createDirectories(deep);
        Files.writeString(deep.resolve("deep.playbook.yaml"), "playbook: '1.0'\n");

        var index = PlaybookIndex.fromDirectory(tempDir);
        assertThat(index.resolve("a/b/deep")).isPresent();
    }

    @Test
    void unknownIdReturnsEmpty() throws IOException {
        var index = PlaybookIndex.fromDirectory(tempDir);
        assertThat(index.resolve("nonexistent")).isEmpty();
    }

    @Test
    void ignoresNonPlaybookFiles() throws IOException {
        Files.writeString(tempDir.resolve("readme.md"), "# Hi\n");
        Files.writeString(tempDir.resolve("test.test.yaml"), "name: test\n");
        Files.writeString(tempDir.resolve("real.playbook.yaml"), "playbook: '1.0'\n");

        var index = PlaybookIndex.fromDirectory(tempDir);
        assertThat(index.size()).isEqualTo(1);
        assertThat(index.resolve("real")).isPresent();
    }
}
