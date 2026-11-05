package io.casehub.yaml.test;

import io.casehub.yaml.core.module.YamlImport;
import io.casehub.yaml.core.module.YamlModule;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemModuleLoaderTest {

    private static final Path PLAYBOOK_ROOT = Path.of("src/test/playbooks");
    private static final Path PLAYBOOK_FILE = PLAYBOOK_ROOT.resolve("simple.yaml");

    @Test
    void loadsModuleFromRelativePath() {
        var loader = new FileSystemModuleLoader();
        var imp = new YamlImport("greetings", "g", null, Map.of());
        Map<String, YamlModule> modules = loader.loadModules(List.of(imp), PLAYBOOK_FILE);

        assertThat(modules).containsKey("greetings");
        YamlModule module = modules.get("greetings");
        assertThat(module.name()).isEqualTo("greetings");
        assertThat(module.parameters()).containsKey("greeting");
        assertThat(module.outputs()).containsKey("full-greeting");
        assertThat(module.sections()).containsKey("steps");
        assertThat(module.sections().get("steps")).containsKey("say-hello");
        assertThat(module.sections().get("steps")).containsKey("say-goodbye");
    }

    @Test
    void missingModuleThrowsWithSearchPaths() {
        var loader = new FileSystemModuleLoader();
        var imp = new YamlImport("nonexistent", "n", null, Map.of());

        assertThatThrownBy(() -> loader.loadModules(List.of(imp), PLAYBOOK_FILE))
                .isInstanceOf(ModuleNotFoundException.class)
                .hasMessageContaining("nonexistent")
                .hasMessageContaining("modules/nonexistent.yaml");
    }

    @Test
    void emptyImportsReturnsEmptyMap() {
        var loader = new FileSystemModuleLoader();
        Map<String, YamlModule> modules = loader.loadModules(List.of(), PLAYBOOK_FILE);
        assertThat(modules).isEmpty();
    }

    @Test
    void pathTraversalModuleNameThrows() {
        var loader = new FileSystemModuleLoader();
        var imp    = new YamlImport("../../etc/passwd", "evil", null, Map.of());

        assertThatThrownBy(() -> loader.loadModules(List.of(imp), PLAYBOOK_FILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void absolutePathModuleNameThrows() {
        var loader = new FileSystemModuleLoader();
        var imp    = new YamlImport("/etc/passwd", "evil", null, Map.of());

        assertThatThrownBy(() -> loader.loadModules(List.of(imp), PLAYBOOK_FILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path traversal");
    }

}
