package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NestedCaptureTest {

    private static StepRunner echoRunner() {
        return (step, resolver) -> {
            if (step instanceof ResolvedStep.PluginStep ps) {
                return Result.of(resolver.resolveMap(ps.params(), "step"));
            }
            throw new IllegalArgumentException("unsupported");
        };
    }

    @Test
    void capture_inside_block_is_collected() {
        var innerStep = new ResolvedStep.PluginStep("inner", "action",
                Map.of("key", "value"), Map.of("capture", "my-capture"));
        var block = new ResolvedStep.BlockStep(null, List.of(innerStep), Map.of());

        var collector = new CaptureCollector();
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null))
                .withCaptureCollector(collector);

        Result result = evaluator.evaluate(block,
                new VariableResolver(Map.of(), Set.of()), echoRunner());

        assertThat(result.isSuccess()).isTrue();
        assertThat(collector.all()).containsKey("my-capture");
        assertThat(collector.all().get("my-capture")).containsEntry("key", "value");
    }

    @Test
    void capture_inside_if_then_is_collected() {
        var innerStep = new ResolvedStep.PluginStep("inner", "action",
                Map.of("data", "result"), Map.of("capture", "if-capture"));
        var ifElse = new ResolvedStep.IfElseStep(null, "true",
                List.of(innerStep), List.of(), Map.of());

        var collector = new CaptureCollector();
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null))
                .withCaptureCollector(collector);

        Result result = evaluator.evaluate(ifElse,
                new VariableResolver(Map.of(), Set.of()), echoRunner());

        assertThat(result.isSuccess()).isTrue();
        assertThat(collector.all()).containsKey("if-capture");
    }

    @Test
    void capture_on_structural_step_is_collected() {
        var innerStep = new ResolvedStep.PluginStep(null, "action",
                Map.of("x", 1), Map.of());
        var block = new ResolvedStep.BlockStep("my-block",
                List.of(innerStep), Map.of("capture", "block-result"));

        var collector = new CaptureCollector();
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null))
                .withCaptureCollector(collector);

        Result result = evaluator.evaluate(block,
                new VariableResolver(Map.of(), Set.of()), echoRunner());

        assertThat(result.isSuccess()).isTrue();
        assertThat(collector.all()).containsKey("block-result");
    }

    @Test
    void captured_value_available_to_downstream_steps() {
        var step1 = new ResolvedStep.PluginStep("s1", "action",
                Map.of("val", "hello"), Map.of("capture", "first"));
        var step2 = new ResolvedStep.PluginStep("s2", "action",
                Map.of("ref", "${first.val}"), Map.of());
        var block = new ResolvedStep.BlockStep(null, List.of(step1, step2), Map.of());

        var collector = new CaptureCollector();
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null))
                .withCaptureCollector(collector);

        Result result = evaluator.evaluate(block,
                new VariableResolver(Map.of(), Set.of()), echoRunner());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("ref", "hello");
    }

    @Test
    void last_write_wins_on_name_collision() {
        var step1 = new ResolvedStep.PluginStep("s1", "action",
                Map.of("v", "first"), Map.of("capture", "data"));
        var step2 = new ResolvedStep.PluginStep("s2", "action",
                Map.of("v", "second"), Map.of("capture", "data"));
        var block = new ResolvedStep.BlockStep(null, List.of(step1, step2), Map.of());

        var collector = new CaptureCollector();
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null))
                .withCaptureCollector(collector);

        evaluator.evaluate(block, new VariableResolver(Map.of(), Set.of()), echoRunner());

        assertThat(collector.all().get("data")).containsEntry("v", "second");
    }

    @Test
    void capture_across_top_level_steps_via_resolver_update() {
        var step1 = new ResolvedStep.PluginStep("s1", "action",
                                                Map.of("val", "hello"), Map.of("capture", "first"));
        var step2 = new ResolvedStep.PluginStep("s2", "action",
                                                Map.of("ref", "${first.val}"), Map.of());

        var collector = new CaptureCollector();
        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null))
                                .withCaptureCollector(collector);

        VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

        Result r1 = evaluator.evaluate(step1, resolver, echoRunner());
        assertThat(r1.isSuccess()).isTrue();

        Object captureVal = step1.decorators().get("capture");
        if (captureVal != null && r1.output() != null) {
            String              name     = String.valueOf(captureVal);
            Map<String, Object> captured = Map.copyOf(r1.output());
            resolver = resolver.withObjectScope(name, captured::get);
        }

        Result r2 = evaluator.evaluate(step2, resolver, echoRunner());
        assertThat(r2.isSuccess()).isTrue();
        assertThat(r2.output()).containsEntry("ref", "hello");
    }

}
