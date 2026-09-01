package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlModuleFileTest {

    @Test
    void toModule_converts_header_and_sections() {
        var header = new YamlModuleFile.YamlModuleHeader("monitor", Map.of(), Map.of());
        var sections = Map.of("nodes", Map.<String, Object>of("cpu-check",
                Map.of("type", "sensor")));
        var file = new YamlModuleFile(header, sections, List.of());
        var module = file.toModule();
        assertThat(module.name()).isEqualTo("monitor");
        assertThat(module.sections()).containsKey("nodes");
    }

    @Test
    void toModule_discards_imports() {
        var header = new YamlModuleFile.YamlModuleHeader("m", Map.of(), Map.of());
        var imp = new YamlImport("other", "alias", null, Map.of());
        var file = new YamlModuleFile(header, Map.of(), List.of(imp));
        var module = file.toModule();
        assertThat(module.sections()).isEmpty();
    }

    @Test
    void null_defaults() {
        var header = new YamlModuleFile.YamlModuleHeader("m", null, null);
        var file = new YamlModuleFile(header, null, null);
        assertThat(header.parameters()).isEmpty();
        assertThat(file.sections()).isEmpty();
        assertThat(file.imports()).isEmpty();
    }

    @Test
    void toModule_includes_outputs() {
        var output = new YamlModuleOutput(ParameterType.STRING, "jdbc:${var.engine}://db");
        var header = new YamlModuleFile.YamlModuleHeader("db",
                                                         Map.of(), Map.of("url", output));
        var file   = new YamlModuleFile(header, Map.of(), List.of());
        var module = file.toModule();
        assertThat(module.outputs()).containsKey("url");
        assertThat(module.outputs().get("url").type()).isEqualTo(ParameterType.STRING);
        assertThat(module.outputs().get("url").value()).isEqualTo("jdbc:${var.engine}://db");
    }

    @Test
    void module_null_outputs_defaults_empty() {
        var module = new YamlModule("m", Map.of(), null, Map.of());
        assertThat(module.outputs()).isEmpty();
    }
}
