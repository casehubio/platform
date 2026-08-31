package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlModuleFileTest {

    @Test
    void toModule_converts_header_and_sections() {
        var header = new YamlModuleFile.YamlModuleHeader("monitor", Map.of());
        var sections = Map.of("nodes", Map.<String, Object>of("cpu-check",
                Map.of("type", "sensor")));
        var file = new YamlModuleFile(header, sections, List.of());
        var module = file.toModule();
        assertThat(module.name()).isEqualTo("monitor");
        assertThat(module.sections()).containsKey("nodes");
    }

    @Test
    void toModule_discards_imports() {
        var header = new YamlModuleFile.YamlModuleHeader("m", Map.of());
        var imp = new YamlImport("other", "alias", null, Map.of());
        var file = new YamlModuleFile(header, Map.of(), List.of(imp));
        var module = file.toModule();
        assertThat(module.sections()).isEmpty();
    }

    @Test
    void null_defaults() {
        var header = new YamlModuleFile.YamlModuleHeader("m", null);
        var file = new YamlModuleFile(header, null, null);
        assertThat(header.parameters()).isEmpty();
        assertThat(file.sections()).isEmpty();
        assertThat(file.imports()).isEmpty();
    }
}
