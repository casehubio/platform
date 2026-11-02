package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.Declaration;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.InvokeHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FilesystemYamlDefinitionSourceTest {

    @TempDir Path tempDir;

    @Test
    void populate_registersActionsFromFilesystemYaml() throws IOException {
        Path stepFile = tempDir.resolve("custom-steps.yaml");
        Files.writeString(stepFile, """
                namespace: custom
                actions:
                  greet:
                    description: Say hello
                    inputs:
                      name:
                        type: string
                        required: true
                    invoke:
                      mcp: greeting-tool
                """);

        InvokeHandler mcpHandler = mcpHandler();

        var source = new FilesystemYamlDefinitionSource(
                List.of(stepFile), List.of(mcpHandler), event -> {});

        PluginRegistry registry = new CompositePluginRegistry();
        source.populate(registry);

        assertThat(registry.resolve("custom.greet")).isPresent();
        assertThat(registry.resolve("greet")).isPresent();
        assertThat(registry.availableActions()).contains("custom.greet", "greet");
    }

    @Test
    void populate_handlesMultipleFiles() throws IOException {
        Path file1 = tempDir.resolve("steps1.yaml");
        Files.writeString(file1, """
                actions:
                  action-one:
                    description: First
                    invoke:
                      mcp: tool-one
                """);

        Path file2 = tempDir.resolve("steps2.yaml");
        Files.writeString(file2, """
                actions:
                  action-two:
                    description: Second
                    invoke:
                      mcp: tool-two
                """);

        var source = new FilesystemYamlDefinitionSource(
                List.of(file1, file2), List.of(mcpHandler()), event -> {});

        PluginRegistry registry = new CompositePluginRegistry();
        source.populate(registry);

        assertThat(registry.resolve("action-one")).isPresent();
        assertThat(registry.resolve("action-two")).isPresent();
    }

    @Test
    void populate_noNamespace_registersWithoutPrefix() throws IOException {
        Path stepFile = tempDir.resolve("steps.yaml");
        Files.writeString(stepFile, """
                actions:
                  simple-action:
                    description: A simple action
                    invoke:
                      mcp: simple-tool
                """);

        var source = new FilesystemYamlDefinitionSource(
                List.of(stepFile), List.of(mcpHandler()), event -> {});

        PluginRegistry registry = new CompositePluginRegistry();
        source.populate(registry);

        assertThat(registry.resolve("simple-action")).isPresent();
        assertThat(registry.availableActions()).containsExactly("simple-action");
    }

    @Test
    void populate_throwsOnMissingFile() {
        Path missing = tempDir.resolve("nonexistent.yaml");

        var source = new FilesystemYamlDefinitionSource(
                List.of(missing), List.of(mcpHandler()), event -> {});

        PluginRegistry registry = new CompositePluginRegistry();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> source.populate(registry))
                                       .isInstanceOf(IllegalStateException.class)
                                       .hasMessageContaining("Failed to load step definition file");
    }


    private static InvokeHandler mcpHandler() {
        return new InvokeHandler() {
            @Override
            public boolean supports(InvokeBinding binding) {
                return binding instanceof InvokeBinding.Mcp;
            }

            @Override
            public io.casehub.yaml.plugin.api.Action create(
                    Declaration decl, InvokeBinding binding) {
                return (params, services) -> Result.of(Map.of());
            }
        };
    }
}
