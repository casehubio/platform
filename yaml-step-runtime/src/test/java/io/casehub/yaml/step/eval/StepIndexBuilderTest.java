package io.casehub.yaml.step.eval;

import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepIndexBuilderTest {

    @Test
    void indexes_named_steps_recursively() {
        var inner = new ResolvedStep.PluginStep("inner-step", "action", Map.of(), Map.of());
        var block = new ResolvedStep.BlockStep("outer-block", List.of(inner), Map.of());
        var top = new ResolvedStep.PluginStep("top-step", "action", Map.of(), Map.of());

        var index = StepIndexBuilder.build(List.of(top, block));

        assertThat(index).hasSize(3);
        assertThat(index.get("top-step")).isSameAs(top);
        assertThat(index.get("outer-block")).isSameAs(block);
        assertThat(index.get("inner-step")).isSameAs(inner);
    }

    @Test
    void unnamed_steps_not_indexed() {
        var unnamed = new ResolvedStep.PluginStep(null, "action", Map.of(), Map.of());
        var index = StepIndexBuilder.build(List.of(unnamed));
        assertThat(index).isEmpty();
    }

    @Test
    void duplicate_names_last_wins() {
        var first = new ResolvedStep.PluginStep("dup", "action-a", Map.of(), Map.of());
        var second = new ResolvedStep.PluginStep("dup", "action-b", Map.of(), Map.of());
        var index = StepIndexBuilder.build(List.of(first, second));
        assertThat(((ResolvedStep.PluginStep) index.get("dup")).actionName()).isEqualTo("action-b");
    }

    @Test
    void indexes_steps_inside_try_catch_finally() {
        var tryStep = new ResolvedStep.PluginStep("try-step", "action", Map.of(), Map.of());
        var catchStep = new ResolvedStep.PluginStep("catch-step", "action", Map.of(), Map.of());
        var finallyStep = new ResolvedStep.PluginStep("finally-step", "action", Map.of(), Map.of());
        var tcf = new ResolvedStep.TryCatchFinallyStep(null,
                List.of(tryStep), List.of(catchStep), List.of(finallyStep), Map.of());

        var index = StepIndexBuilder.build(List.of(tcf));

        assertThat(index).containsKeys("try-step", "catch-step", "finally-step");
    }

    @Test
    void indexes_steps_inside_if_else() {
        var thenStep = new ResolvedStep.PluginStep("then-step", "action", Map.of(), Map.of());
        var elseStep = new ResolvedStep.PluginStep("else-step", "action", Map.of(), Map.of());
        var ifElse = new ResolvedStep.IfElseStep(null, "true",
                List.of(thenStep), List.of(elseStep), Map.of());

        var index = StepIndexBuilder.build(List.of(ifElse));

        assertThat(index).containsKeys("then-step", "else-step");
    }
}
