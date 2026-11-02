package io.casehub.yaml.step.catalog;

import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalkerSiblingAbsorptionTest {

    private static Definition absorbingPlugin(String name) {
        return Definition.of(name)
                .execute((params, services) -> Result.of(params))
                .absorbSiblings(true)
                .build();
    }

    private static Definition regularPlugin(String name) {
        return Definition.of(name)
                .execute((params, services) -> Result.of(params))
                .build();
    }

    private static CompositePluginRegistry registryWith(Definition... defs) {
        var registry = new CompositePluginRegistry();
        for (var d : defs) registry.register(d);
        return registry;
    }

    @Test
    void siblingAbsorbingPluginCollectsSiblingKeys() {
        var registry = registryWith(absorbingPlugin("assertThat"));

        var step = new LinkedHashMap<String, Object>();
        step.put("assertThat", "${response}");
        step.put("contains", "equities");
        step.put("startsWith", "Moderate");

        var resolved = Walker.resolve(List.of(step), registry);
        assertThat(resolved).hasSize(1);
        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.actionName()).isEqualTo("assertThat");
        assertThat(plugin.params().get("_subject")).isEqualTo("${response}");
        assertThat(plugin.params().get("contains")).isEqualTo("equities");
        assertThat(plugin.params().get("startsWith")).isEqualTo("Moderate");
    }

    @Test
    void nonAbsorbingPluginThrowsOnUnknownSiblingKeys() {
        var registry = registryWith(regularPlugin("assert"));

        var step = new LinkedHashMap<String, Object>();
        step.put("assert", Map.of("expression", "true"));
        step.put("contains", "equities");

        assertThatThrownBy(() -> Walker.resolve(List.of(step), registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown key 'contains'");
    }

    @Test
    void scalarValueBecomesSubjectParam() {
        var registry = registryWith(regularPlugin("run-playbook"));

        var step = Map.<String, Object>of("run-playbook", "examples/02.yaml");
        var resolved = Walker.resolve(List.of(step), registry);
        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.params().get("_subject")).isEqualTo("examples/02.yaml");
    }

    @Test
    void mapValueBecomesParamsDirectly() {
        var registry = registryWith(regularPlugin("run-playbook"));

        var step = Map.<String, Object>of("run-playbook",
                Map.of("file", "examples/02.yaml", "vars", Map.of("k", "v")));
        var resolved = Walker.resolve(List.of(step), registry);
        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.params().get("file")).isEqualTo("examples/02.yaml");
        assertThat(plugin.params()).doesNotContainKey("_subject");
    }

    @Test
    void captureExtractedAsDecorator() {
        var registry = registryWith(regularPlugin("agent-invoke"));

        var step = new LinkedHashMap<String, Object>();
        step.put("agent-invoke", Map.of("prompt", "hello"));
        step.put("capture", "result");

        var resolved = Walker.resolve(List.of(step), registry);
        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.params()).doesNotContainKey("capture");
        assertThat(plugin.decorators().get("capture")).isEqualTo("result");
    }

    @Test
    void absorbingSiblingWithCaptureDecorator() {
        var registry = registryWith(absorbingPlugin("emit-event"));

        var step = new LinkedHashMap<String, Object>();
        step.put("emit-event", "capacity.pressure");
        step.put("data", Map.of("actorId", "agent-001"));
        step.put("capture", "result");

        var resolved = Walker.resolve(List.of(step), registry);
        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.params().get("_subject")).isEqualTo("capacity.pressure");
        assertThat(plugin.params().get("data")).isEqualTo(Map.of("actorId", "agent-001"));
        assertThat(plugin.params()).doesNotContainKey("capture");
        assertThat(plugin.decorators().get("capture")).isEqualTo("result");
    }

    @Test
    void scalarPluginWithCaptureDecorator() {
        var registry = registryWith(regularPlugin("run-playbook"));

        var step = new LinkedHashMap<String, Object>();
        step.put("run-playbook", "examples/02.yaml");
        step.put("capture", "results");

        var resolved = Walker.resolve(List.of(step), registry);
        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.params().get("_subject")).isEqualTo("examples/02.yaml");
        assertThat(plugin.decorators().get("capture")).isEqualTo("results");
    }
}
