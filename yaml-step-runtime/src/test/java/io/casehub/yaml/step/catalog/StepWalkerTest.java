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
        step.put("if", "${env.ready}");
        step.put("timeout", "30s");

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.decorators()).containsEntry("if", "${env.ready}");
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
        step.put("if", "true");
        step.put("retry", Map.of("max", 3));
        step.put("on-error", "fallback");
        step.put("on-success", "next");
        step.put("on-failure", "abort");

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        var plugin = (ResolvedStep.PluginStep) resolved.get(0);
        assertThat(plugin.decorators()).containsKeys("if", "retry", "on-error",
                "on-success", "on-failure");
        assertThat(plugin.params()).containsEntry("command", "test.sh");
    }


    @Test
    void resolvesBlockStep() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("block", List.of(
                Map.of("process", Map.of("command", "a.sh")),
                Map.of("assert", Map.of("expression", "true"))
                                 ));
        step.put("loop", Map.of("count", 3));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.BlockStep.class);
        var block = (ResolvedStep.BlockStep) resolved.get(0);
        assertThat(block.steps()).hasSize(2);
        assertThat(block.decorators()).containsKey("loop");
    }

    @Test
    void resolvesIfElseStep() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("if", "${risk} == 'HIGH'");
        step.put("then", List.of(Map.of("process", Map.of("command", "escalate.sh"))));
        step.put("else", List.of(Map.of("process", Map.of("command", "proceed.sh"))));
        step.put("timeout", "30s");

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.IfElseStep.class);
        var ifElse = (ResolvedStep.IfElseStep) resolved.get(0);
        assertThat(ifElse.condition()).isEqualTo("${risk} == 'HIGH'");
        assertThat(ifElse.thenSteps()).hasSize(1);
        assertThat(ifElse.elseSteps()).hasSize(1);
        assertThat(ifElse.decorators()).containsKey("timeout");
        assertThat(ifElse.decorators()).doesNotContainKey("if");
    }

    @Test
    void resolvesIfWithoutElse() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("if", "${enabled}");
        step.put("then", List.of(Map.of("process", Map.of("command", "a.sh"))));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        var ifElse = (ResolvedStep.IfElseStep) resolved.get(0);
        assertThat(ifElse.elseSteps()).isEmpty();
    }

    @Test
    void ifWithoutThenIsDecorator() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("process", Map.of("command", "a.sh"));
        step.put("if", "${enabled}");

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.PluginStep.class);
        assertThat(resolved.get(0).decorators()).containsKey("if");
    }

    @Test
    void resolvesMatchStep() {
        Map<String, Object> caseEntry = new LinkedHashMap<>();
        caseEntry.put("pattern", Map.of("type", "trade"));
        caseEntry.put("guard", "${match.amount} > 1000000");
        caseEntry.put("steps", List.of(Map.of("process", Map.of("command", "escalate.sh"))));

        Map<String, Object> defaultEntry = new LinkedHashMap<>();
        defaultEntry.put("default", List.of(Map.of("process", Map.of("command", "log.sh"))));

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("match", "${event}");
        step.put("cases", List.of(caseEntry, defaultEntry));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.MatchStep.class);
        var match = (ResolvedStep.MatchStep) resolved.get(0);
        assertThat(match.scrutinee()).isEqualTo("${event}");
        assertThat(match.cases()).hasSize(2);
        assertThat(match.cases().get(0).pattern()).isInstanceOf(io.casehub.yaml.core.step.MatchPattern.StructuralPattern.class);
        assertThat(match.cases().get(0).guard()).isEqualTo("${match.amount} > 1000000");
        assertThat(match.cases().get(1).pattern()).isInstanceOf(io.casehub.yaml.core.step.MatchPattern.DefaultPattern.class);
    }

    @Test
    void resolvesParallelStep() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("parallel", List.of(
                Map.of("process", Map.of("command", "a.sh")),
                Map.of("process", Map.of("command", "b.sh"))
                                    ));
        step.put("timeout", "60s");

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(step), catalog);

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0)).isInstanceOf(ResolvedStep.ParallelStep.class);
        var par = (ResolvedStep.ParallelStep) resolved.get(0);
        assertThat(par.steps()).hasSize(2);
        assertThat(par.decorators()).containsKey("timeout");
    }

    @Test
    void rejectsAmbiguousStructuralPlusAction() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("block", List.of(Map.of("process", Map.of("command", "a.sh"))));
        step.put("process", Map.of("command", "b.sh"));

        assertThatThrownBy(() -> StepWalker.resolve(List.of(step), catalog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    void rejectsOrphanedThen() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("process", Map.of("command", "a.sh"));
        step.put("then", List.of(Map.of("process", Map.of("command", "b.sh"))));

        assertThatThrownBy(() -> StepWalker.resolve(List.of(step), catalog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("then");
    }

    @Test
    void rejectsElseWithoutThen() {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("process", Map.of("command", "a.sh"));
        step.put("else", List.of(Map.of("process", Map.of("command", "b.sh"))));

        assertThatThrownBy(() -> StepWalker.resolve(List.of(step), catalog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("else");
    }

    @Test
    void rejectsDefaultNotLast() {
        Map<String, Object> defaultEntry = new LinkedHashMap<>();
        defaultEntry.put("default", List.of(Map.of("process", Map.of("command", "log.sh"))));
        Map<String, Object> caseEntry = new LinkedHashMap<>();
        caseEntry.put("pattern", "ACTIVE");
        caseEntry.put("steps", List.of(Map.of("process", Map.of("command", "a.sh"))));

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("match", "${status}");
        step.put("cases", List.of(defaultEntry, caseEntry));

        assertThatThrownBy(() -> StepWalker.resolve(List.of(step), catalog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default must be the last case");
    }


    @Test
    void rejectsCaseWithoutPatternOrDefault() {
        Map<String, Object> caseEntry = new LinkedHashMap<>();
        caseEntry.put("guard", "${match.amount} > 1000");
        caseEntry.put("steps", List.of(Map.of("process", Map.of("command", "a.sh"))));

        Map<String, Object> step = new LinkedHashMap<>();
        step.put("match", "${status}");
        step.put("cases", List.of(caseEntry));

        assertThatThrownBy(() -> StepWalker.resolve(List.of(step), catalog))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain either 'pattern' or 'default'");
    }

    @Test
    void recursivelyResolvesNestedBlocks() {
        Map<String, Object> innerBlock = new LinkedHashMap<>();
        innerBlock.put("block", List.of(
                Map.of("process", Map.of("command", "inner.sh"))));
        innerBlock.put("loop", Map.of("count", 2));

        Map<String, Object> outerBlock = new LinkedHashMap<>();
        outerBlock.put("block", List.of(
                Map.of("process", Map.of("command", "outer.sh")),
                innerBlock));

        List<ResolvedStep> resolved = StepWalker.resolve(List.of(outerBlock), catalog);

        var outer = (ResolvedStep.BlockStep) resolved.get(0);
        assertThat(outer.steps()).hasSize(2);
        assertThat(outer.steps().get(1)).isInstanceOf(ResolvedStep.BlockStep.class);
        var inner = (ResolvedStep.BlockStep) outer.steps().get(1);
        assertThat(inner.steps()).hasSize(1);
        assertThat(inner.decorators()).containsKey("loop");
    }

    private static CatalogEntry entry(String name) {
        var def = new StepDefinition(name, null, Map.of(), Map.of(),
                new InvokeBinding.Mcp(name));
        return new CatalogEntry(name, def,
                (params, services) -> StepResult.of(Map.of()));
    }
}
