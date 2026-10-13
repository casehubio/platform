package io.casehub.yaml.step.catalog;

import io.casehub.yaml.core.step.MatchPattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResolvedStepTest {

    @Test
    void blockStepHoldsStepsAndDecorators() {
        var inner = new ResolvedStep.InvokeStep(null, Map.of("mcp", "tool"), Map.of());
        var block = new ResolvedStep.BlockStep(null, List.of(inner), Map.of("loop", Map.of("count", 3)));
        assertThat(block.steps()).hasSize(1);
        assertThat(block.decorators()).containsKey("loop");
    }

    @Test
    void blockStepDefensiveCopy() {
        var inner = new ResolvedStep.InvokeStep(null, Map.of("mcp", "tool"), Map.of());
        var mutableList = new java.util.ArrayList<>(List.<ResolvedStep>of(inner));
        var block = new ResolvedStep.BlockStep(null, mutableList, Map.of());
        mutableList.clear();
        assertThat(block.steps()).hasSize(1);
    }

    @Test
    void ifElseStepDefaultsElseToEmptyList() {
        var then = new ResolvedStep.InvokeStep(null, Map.of("mcp", "a"), Map.of());
        var step = new ResolvedStep.IfElseStep(null, "${x}", List.of(then), null, Map.of());
        assertThat(step.elseSteps()).isEmpty();
    }

    @Test
    void ifElseStepHoldsBothBranches() {
        var thenStep = new ResolvedStep.InvokeStep(null, Map.of("mcp", "a"), Map.of());
        var elseStep = new ResolvedStep.InvokeStep(null, Map.of("mcp", "b"), Map.of());
        var step = new ResolvedStep.IfElseStep(null, "${risk} == 'HIGH'",
                List.of(thenStep), List.of(elseStep), Map.of("timeout", "30s"));
        assertThat(step.condition()).isEqualTo("${risk} == 'HIGH'");
        assertThat(step.thenSteps()).hasSize(1);
        assertThat(step.elseSteps()).hasSize(1);
        assertThat(step.decorators()).containsKey("timeout");
    }

    @Test
    void matchStepHoldsCases() {
        var pattern = new MatchPattern.ValuePattern("ACTIVE");
        var inner = new ResolvedStep.InvokeStep(null, Map.of("mcp", "a"), Map.of());
        var mc = new ResolvedMatchCase(pattern, null, List.of(inner));
        var step = new ResolvedStep.MatchStep(null, "${status}", List.of(mc), Map.of());
        assertThat(step.cases()).hasSize(1);
        assertThat(step.cases().get(0).pattern()).isEqualTo(pattern);
        assertThat(step.scrutinee()).isEqualTo("${status}");
    }

    @Test
    void matchStepWithGuardedCase() {
        var pattern = new MatchPattern.StructuralPattern(Map.of("type", "trade"));
        var inner = new ResolvedStep.InvokeStep(null, Map.of("mcp", "escalate"), Map.of());
        var mc = new ResolvedMatchCase(pattern, "${match.amount} > 1000000", List.of(inner));
        assertThat(mc.guard()).isEqualTo("${match.amount} > 1000000");
    }

    @Test
    void parallelStepHoldsSteps() {
        var a = new ResolvedStep.InvokeStep(null, Map.of("mcp", "a"), Map.of());
        var b = new ResolvedStep.InvokeStep(null, Map.of("mcp", "b"), Map.of());
        var step = new ResolvedStep.ParallelStep(null, List.of(a, b), Map.of("timeout", "30s"));
        assertThat(step.steps()).hasSize(2);
        assertThat(step.decorators()).containsKey("timeout");
    }

    @Test
    void allVariantsAreResolvedStep() {
        ResolvedStep plugin = new ResolvedStep.PluginStep(null, null, Map.of(), Map.of());
        ResolvedStep invoke = new ResolvedStep.InvokeStep(null, Map.of(), Map.of());
        ResolvedStep block = new ResolvedStep.BlockStep(null, List.of(), Map.of());
        ResolvedStep ifElse = new ResolvedStep.IfElseStep(null, "true", List.of(), null, Map.of());
        ResolvedStep match = new ResolvedStep.MatchStep(null, "${x}", List.of(), Map.of());
        ResolvedStep parallel = new ResolvedStep.ParallelStep(null, List.of(), Map.of());
        assertThat(List.of(plugin, invoke, block, ifElse, match, parallel))
                .allSatisfy(s -> assertThat(s).isInstanceOf(ResolvedStep.class));
    }

    private ResolvedStep leaf(String id) {
        return new ResolvedStep.InvokeStep(null, Map.of("id", id), Map.of());
    }

    @Test
    void parallelStep_defaultPolicyFields() {
        var steps = List.<ResolvedStep>of(leaf("a"), leaf("b"));
        var parallel = new ResolvedStep.ParallelStep(
                null, steps, steps.size(), false, null, Map.of());
        assertThat(parallel.advanceAfter()).isEqualTo(2);
        assertThat(parallel.cancelRemaining()).isFalse();
        assertThat(parallel.collectionTimeout()).isNull();
    }

    @Test
    void parallelStep_discriminatorPolicy() {
        var steps = List.<ResolvedStep>of(leaf("a"), leaf("b"), leaf("c"));
        var parallel = new ResolvedStep.ParallelStep(
                null, steps, 1, false, null, Map.of());
        assertThat(parallel.advanceAfter()).isEqualTo(1);
        assertThat(parallel.cancelRemaining()).isFalse();
    }

    @Test
    void parallelStep_racePolicy() {
        var steps = List.<ResolvedStep>of(leaf("a"), leaf("b"));
        var parallel = new ResolvedStep.ParallelStep(
                null, steps, 1, true, null, Map.of());
        assertThat(parallel.cancelRemaining()).isTrue();
    }

    @Test
    void parallelStep_collectionTimeout() {
        var steps = List.<ResolvedStep>of(leaf("a"), leaf("b"));
        var parallel = new ResolvedStep.ParallelStep(
                null, steps, 1, false, java.time.Duration.ofSeconds(30), Map.of());
        assertThat(parallel.collectionTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
    }

    @Test
    void parallelStep_advanceAfterExceedsSteps_throws() {
        var steps = List.<ResolvedStep>of(leaf("a"));
        assertThatThrownBy(() -> new ResolvedStep.ParallelStep(
                null, steps, 5, false, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parallelStep_advanceAfterZero_throws() {
        var steps = List.<ResolvedStep>of(leaf("a"));
        assertThatThrownBy(() -> new ResolvedStep.ParallelStep(
                null, steps, 0, false, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parallelStep_legacyConstructor_setsDefaults() {
        var steps    = List.<ResolvedStep>of(leaf("a"), leaf("b"));
        var parallel = new ResolvedStep.ParallelStep(null, steps, Map.of());
        assertThat(parallel.advanceAfter()).isEqualTo(2);
        assertThat(parallel.cancelRemaining()).isFalse();
        assertThat(parallel.collectionTimeout()).isNull();
    }

}
