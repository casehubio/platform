package io.casehub.yaml.core.module;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleBridgeExpandTest {

    record TestContent(Map<String, Object> nodes,
                       Map<String, Object> rules) {}

    static class TestBridge implements ModuleBridge<TestContent> {

        @Override
        public TestContent fromSections(Map<String, Map<String, Object>> sections) {
            return new TestContent(
                    sections.getOrDefault("nodes", Map.of()),
                    sections.getOrDefault("rules", Map.of()));
        }

        @Override
        public Map<String, Map<String, Object>> toSections(TestContent content) {
            Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
            if (!content.nodes().isEmpty()) sections.put("nodes", content.nodes());
            if (!content.rules().isEmpty()) sections.put("rules", content.rules());
            return sections;
        }
    }

    @Test
    void typed_expand_converts_at_boundaries() {
        var module = new YamlModule("monitoring",
                Map.of("region", new YamlModuleParameter(ParameterType.STRING, true,
                        null, null, null, null, null, null, List.of(), null)),
                Map.of(),
                Map.of("nodes", Map.of("monitor", Map.of("type", "http-poller"))));

        var imports = List.of(new YamlImport("monitoring", "mon", null, Map.of("region", "us-east")));
        var existingContent = new TestContent(Map.of(), Map.of());
        var bridge = new TestBridge();

        TypedExpandedModule<TestContent> result = ModuleExpander.expand(
                imports, Map.of("monitoring", module), existingContent, bridge);

        assertThat(result.content().nodes()).containsKey("mon.monitor");
        @SuppressWarnings("unchecked")
        Map<String, Object> monitor = (Map<String, Object>) result.content().nodes().get("mon.monitor");
        assertThat(monitor).containsEntry("type", "http-poller");
    }

    @Test
    void typed_expand_rewriter_applied() {
        var module = new YamlModule("m",
                Map.of(),
                Map.of(),
                Map.of("nodes", Map.of("a", Map.of("dep", "b"), "b", Map.of("type", "target"))));

        var bridge = new ModuleBridge<TestContent>() {
            @Override
            public TestContent fromSections(Map<String, Map<String, Object>> sections) {
                return new TestContent(
                        sections.getOrDefault("nodes", Map.of()),
                        sections.getOrDefault("rules", Map.of()));
            }
            @Override
            public Map<String, Map<String, Object>> toSections(TestContent content) {
                Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
                if (!content.nodes().isEmpty()) sections.put("nodes", content.nodes());
                return sections;
            }
            @Override
            public SectionContentRewriter rewriter() {
                return (sectionName, entryKey, entryValue, alias, moduleKeys) -> {
                    if (entryValue instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) entryValue);
                        if (map.containsKey("dep") && moduleKeys.contains((String) map.get("dep"))) {
                            map.put("dep", alias + "." + map.get("dep"));
                        }
                        return map;
                    }
                    return entryValue;
                };
            }
        };

        var imports = List.of(new YamlImport("m", "x", null, Map.of()));
        var result = ModuleExpander.expand(
                imports, Map.of("m", module), new TestContent(Map.of(), Map.of()), bridge);

        @SuppressWarnings("unchecked")
        Map<String, Object> nodeA = (Map<String, Object>) result.content().nodes().get("x.a");
        assertThat(nodeA).containsEntry("dep", "x.b");
    }

    @Test
    void typed_expand_null_rewriter_no_error() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                Map.of("nodes", Map.of("a", Map.of("k", "v"))));

        var imports = List.of(new YamlImport("m", "x", null, Map.of()));
        var bridge = new TestBridge();

        TypedExpandedModule<TestContent> result = ModuleExpander.expand(
                imports, Map.of("m", module), new TestContent(Map.of(), Map.of()), bridge);

        assertThat(result.content().nodes()).containsKey("x.a");
    }

    @Test
    void typed_expand_preserves_module_scopes() {
        var module = new YamlModule("m",
                Map.of("region", new YamlModuleParameter(ParameterType.STRING, false,
                        "default", null, null, null, null, null, List.of(), null)),
                Map.of(),
                Map.of("nodes", Map.of("a", Map.of("k", "v"))));

        var imports = List.of(new YamlImport("m", "x", null, Map.of("region", "us-east")));
        var bridge = new TestBridge();

        var result = ModuleExpander.expand(
                imports, Map.of("m", module), new TestContent(Map.of(), Map.of()), bridge);

        assertThat(result.moduleScopes()).containsKey("x");
        assertThat(result.moduleScopes().get("x")).containsEntry("region", "us-east");
    }

    @Test
    void typed_expand_preserves_import_conditions() {
        var module = new YamlModule("m", Map.of(), Map.of(),
                Map.of("nodes", Map.of("a", Map.of("k", "v"))));

        var imports = List.of(new YamlImport("m", "x", "env == 'prod'", Map.of()));
        var bridge = new TestBridge();

        var result = ModuleExpander.expand(
                imports, Map.of("m", module), new TestContent(Map.of(), Map.of()), bridge);

        assertThat(result.importConditions()).containsEntry("x", "env == 'prod'");
    }

    @Test
    void typed_expand_preserves_module_outputs() {
        var module = new YamlModule("m", Map.of(),
                Map.of("endpoint", new YamlModuleOutput(ParameterType.STRING, "https://example.com")),
                Map.of("nodes", Map.of("a", Map.of("k", "v"))));

        var imports = List.of(new YamlImport("m", "x", null, Map.of()));
        var bridge = new TestBridge();

        var result = ModuleExpander.expand(
                imports, Map.of("m", module), new TestContent(Map.of(), Map.of()), bridge);

        assertThat(result.moduleOutputs()).containsKey("x");
        assertThat(result.moduleOutputs().get("x")).containsEntry("endpoint", "https://example.com");
    }

    @Test
    void typed_expand_output_source_resolves() {
        var module = new YamlModule("m", Map.of(),
                Map.of("endpoint", new YamlModuleOutput(ParameterType.STRING, "https://example.com")),
                Map.of("nodes", Map.of("a", Map.of("k", "v"))));

        var imports = List.of(new YamlImport("m", "x", null, Map.of()));
        var bridge = new TestBridge();

        var result = ModuleExpander.expand(
                imports, Map.of("m", module), new TestContent(Map.of(), Map.of()), bridge);

        assertThat(result.outputSource().resolve("x.endpoint"))
                .isEqualTo("https://example.com");
    }
}
