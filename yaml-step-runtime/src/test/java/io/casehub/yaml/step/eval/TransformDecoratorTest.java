package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TransformDecoratorTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void transform_applies_expression_to_output() {
        TransformDelegate delegate = (expr, input) -> Map.of("doubled", ((Number) input.get("x")).intValue() * 2);
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                null, null, null, delegate);
        var chain = new DecoratorChain(config);

        DecoratedExecution inner = ctx -> Result.of(Map.of("x", 5));
        DecoratedExecution wrapped = chain.apply(Map.of("transform", "double(x)"), inner);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("doubled", 10);
    }

    @Test
    void transform_failure_fails_step() {
        TransformDelegate delegate = (expr, input) -> { throw new RuntimeException("bad expression"); };
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                null, null, null, delegate);
        var chain = new DecoratorChain(config);

        DecoratedExecution inner = ctx -> Result.of(Map.of("x", 5));
        DecoratedExecution wrapped = chain.apply(Map.of("transform", "bad"), inner);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("Transform failed");
    }

    @Test
    void no_transform_delegate_returns_error() {
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                null, null, null, null);
        var chain = new DecoratorChain(config);

        DecoratedExecution inner = ctx -> Result.of(Map.of("x", 5));
        DecoratedExecution wrapped = chain.apply(Map.of("transform", "expr"), inner);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("requires an expression engine");
    }

    @Test
    void transform_not_applied_on_failure() {
        TransformDelegate delegate = (expr, input) -> { throw new AssertionError("should not be called"); };
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                null, null, null, delegate);
        var chain = new DecoratorChain(config);

        DecoratedExecution inner = ctx -> Result.failed("boom");
        DecoratedExecution wrapped = chain.apply(Map.of("transform", "expr"), inner);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isFalse();
    }
}
