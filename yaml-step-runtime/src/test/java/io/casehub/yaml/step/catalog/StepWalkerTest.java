package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.StepCatalog;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StepWalkerTest {

    private final CatalogEntry processEntry = entry("process");
    private final CatalogEntry assertEntry = entry("assert");

    private final StepCatalog catalog = new StepCatalog() {
        private final Map<String, CatalogEntry> entries = Map.of(
                "process", processEntry, "assert", assertEntry);

        @Override
        public Optional<CatalogEntry> resolve(String actionName) {
            return Optional.ofNullable(entries.get(actionName));
        }

        @Override
        public Set<String> availableActions() {
            return entries.keySet();
        }
    };

    @Test
    void resolvesPluginNameAsKey() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("process", Map.of("command", "deploy.sh"));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.PluginStep.class);
        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.entry().qualifiedName()).isEqualTo("process");
        assertThat(plugin.params()).containsEntry("command", "deploy.sh");
        assertThat(plugin.decorators()).isEmpty();
    }

    @Test
    void extractsDecoratorsFromPluginStep() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("process", Map.of("command", "deploy.sh"));
        step.put("when", "${env.ready}");
        step.put("timeout", "30s");

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.decorators()).containsEntry("when", "${env.ready}");
        assertThat(plugin.decorators()).containsEntry("timeout", "30s");
    }

    @Test
    void resolvesInvokeEscapeHatch() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("invoke", Map.of("mcp", Map.of("tool", "custom-tool")));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.InvokeStep.class);
        var invoke = (ResolvedStep.InvokeStep) resolved.get(0);
        assertThat(invoke.invokeSpec()).containsKey("mcp");
    }

    @Test
    void extractsStepLabelIgnoringItAsAction() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("step", "deploy-prod");
        step.put("process", Map.of("command", "deploy.sh"));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.entry().qualifiedName()).isEqualTo("process");
        assertThat(plugin.decorators()).doesNotContainKey("step");
    }

    @Test
    void throwsOnUnknownAction() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("unknown-action", Map.of("param", "value"));

        assertThatThrownBy(() -> StepWalker.resolve(List.of(step), catalog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown-action")
                .hasMessageContaining("process")
                .hasMessageContaining("assert");
    }

    @Test
    void throwsOnEmptyStepMap() {
        Map<String, Object> step = new LinkedHashMap<>();

        assertThatThrownBy(() -> StepWalker.resolve(List.of(step), catalog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void resolvesMultipleSteps() {
        Map<String, Object> step1 = new LinkedHashMap<>();
        step1.put("process", Map.of("command", "build.sh"));
        Map<String, Object> step2 = new LinkedHashMap<>();
        step2.put("assert", Map.of("expression", "true"));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step1, step2), catalog);

        assertThat(resolved).hasSize(2);
        assertThat(((ResolvedStep.PluginStep) resolved.get(0)).entry().qualifiedName())
                .isEqualTo("process");
        assertThat(((ResolvedStep.PluginStep) resolved.get(1)).entry().qualifiedName())
                .isEqualTo("assert");
    }

    @Test
    void allReservedKeysAreDecorators() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("process", Map.of("command", "test.sh"));
        step.put("when", "true");
        step.put("retry", Map.of("max", 3));
        step.put("on-error", "fallback");
        step.put("on-success", "next");
        step.put("on-failure", "abort");

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.decorators()).containsKeys("when", "retry", "on-error",
                "on-success", "on-failure");
        assertThat(plugin.params()).containsEntry("command", "test.sh");
    }

    private static CatalogEntry entry(String name) {
        var def = new StepDefinition(name, null, Map.of(), Map.of(),
                new InvokeBinding.Mcp(name));
        return new CatalogEntry(name, def,
                (params, services) -> StepResult.of(Map.of()));
    }
}
