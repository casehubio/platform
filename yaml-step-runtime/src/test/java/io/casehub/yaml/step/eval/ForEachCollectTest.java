package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ForEachCollectTest {

    private final VariableResolver baseResolver = new VariableResolver(Map.of(), Set.of());

    private VariableResolver withItems(List<?> items) {
        return baseResolver.withObjectScope("var", name -> "items".equals(name) ? items : null);
    }

    @SuppressWarnings("unchecked")
    @Test
    void collectAllReturnsListOfResults() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var resolver = withItems(List.of("a", "b", "c"));

        DecoratedExecution inner = ctx ->
                Result.of(Map.of("value", ctx.resolver().resolveString("${each.item}", "test")));

        var decorated = chain.apply(
                Map.of("forEach", Map.of("in", "${var.items}", "as", "item", "collect", "all")),
                inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isTrue();
        var collected = (List<Map<String, Object>>) result.output().get("collected");
        assertThat(collected).hasSize(3);
        assertThat(collected.get(0).get("value")).isEqualTo("a");
        assertThat(collected.get(1).get("value")).isEqualTo("b");
        assertThat(collected.get(2).get("value")).isEqualTo("c");
    }

    @Test
    void withoutCollectReturnsLastResult() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var resolver = withItems(List.of("a", "b", "c"));

        DecoratedExecution inner = ctx ->
                Result.of(Map.of("value", ctx.resolver().resolveString("${each.item}", "test")));

        var decorated = chain.apply(
                Map.of("forEach", Map.of("in", "${var.items}", "as", "item")),
                inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("value")).isEqualTo("c");
    }

    @SuppressWarnings("unchecked")
    @Test
    void collectAllWithParallel() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var resolver = withItems(List.of("x", "y"));

        DecoratedExecution inner = ctx ->
                Result.of(Map.of("value", ctx.resolver().resolveString("${each.item}", "test")));

        var decorated = chain.apply(
                Map.of("forEach", Map.of("in", "${var.items}", "as", "item",
                        "collect", "all", "parallel", true)),
                inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isTrue();
        var collected = (List<Map<String, Object>>) result.output().get("collected");
        assertThat(collected).hasSize(2);
    }

    @Test
    void collectStopsOnFailure() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var resolver = withItems(List.of("a", "b", "c"));

        DecoratedExecution inner = ctx -> {
            String val = ctx.resolver().resolveString("${each.item}", "test");
            if ("b".equals(val)) return Result.failed("bad item");
            return Result.of(Map.of("value", val));
        };

        var decorated = chain.apply(
                Map.of("forEach", Map.of("in", "${var.items}", "as", "item", "collect", "all")),
                inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isFalse();
    }
}
