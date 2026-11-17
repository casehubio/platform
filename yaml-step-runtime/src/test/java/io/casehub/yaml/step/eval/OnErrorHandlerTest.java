package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OnErrorHandlerTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void handler_runs_on_failure_and_recovers() {
        var handler = new ResolvedStep.PluginStep("recovery", "recovery-action", Map.of(), Map.of());
        StepLookup lookup = name -> "recovery".equals(name) ? handler : null;
        StepEvaluator evaluator = (step, ctx, runner) -> Result.of(Map.of("recovered", true));
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                lookup, evaluator, (step, r) -> Result.of(Map.of()), null);
        var chain = new DecoratorChain(config);

        DecoratedExecution failing = ctx -> Result.failed("boom");
        DecoratedExecution wrapped = chain.apply(Map.of("on-error", "recovery"), failing);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("recovered", true);
    }

    @Test
    void handler_failure_propagates_original_error() {
        var handler = new ResolvedStep.PluginStep("recovery", "recovery-action", Map.of(), Map.of());
        StepLookup lookup = name -> handler;
        StepEvaluator evaluator = (step, ctx, runner) -> Result.failed("handler also failed");
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                lookup, evaluator, (step, r) -> Result.failed("handler also failed"), null);
        var chain = new DecoratorChain(config);

        DecoratedExecution failing = ctx -> Result.failed("original error");
        DecoratedExecution wrapped = chain.apply(Map.of("on-error", "recovery"), failing);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isFalse();
        assertThat(((Result.Failure) result).message()).contains("original error");
    }

    @Test
    void missing_handler_propagates_original_error() {
        StepLookup lookup = name -> null;
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                lookup, null, null, null);
        var chain = new DecoratorChain(config);

        DecoratedExecution failing = ctx -> Result.failed("boom");
        DecoratedExecution wrapped = chain.apply(Map.of("on-error", "nonexistent"), failing);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void no_step_lookup_propagates_original_error() {
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                null, null, null, null);
        var chain = new DecoratorChain(config);

        DecoratedExecution failing = ctx -> Result.failed("boom");
        DecoratedExecution wrapped = chain.apply(Map.of("on-error", "recovery"), failing);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void handler_receives_error_context() {
        var handler = new ResolvedStep.PluginStep("recovery", "recovery-action", Map.of(), Map.of());
        StepLookup lookup = name -> handler;
        String[] capturedType = {null};
        String[] capturedMsg = {null};
        StepEvaluator evaluator = (step, ctx, runner) -> {
            capturedType[0] = (String) ctx.resolver().resolve("${error.type}");
            capturedMsg[0] = (String) ctx.resolver().resolve("${error.message}");
            return Result.of(Map.of("recovered", true));
        };
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                lookup, evaluator, (step, r) -> Result.of(Map.of()), null);
        var chain = new DecoratorChain(config);

        DecoratedExecution failing = ctx -> Result.failed("something broke", "timeout");
        DecoratedExecution wrapped = chain.apply(Map.of("on-error", "recovery"), failing);

        wrapped.execute(new StepContext(resolver));
        assertThat(capturedType[0]).isEqualTo("timeout");
        assertThat(capturedMsg[0]).isEqualTo("something broke");
    }

    @Test
    void success_bypasses_handler() {
        StepLookup lookup = name -> { throw new AssertionError("should not be called"); };
        var config = new DecoratorConfig(
                new ConditionEvaluator(null), SpeedMultiplier.identity(), null,
                lookup, null, null, null);
        var chain = new DecoratorChain(config);

        DecoratedExecution success = ctx -> Result.of(Map.of("ok", true));
        DecoratedExecution wrapped = chain.apply(Map.of("on-error", "recovery"), success);

        Result result = wrapped.execute(new StepContext(resolver));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).containsEntry("ok", true);
    }
}
