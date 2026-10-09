package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DefaultExecutionScope;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class WrapAtDecoratorTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void blockingWaitUntilThresholdMet() throws Exception {
        var scope = new DefaultExecutionScope();
        var counter = scope.counter("supply");
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var executed = new CompletableFuture<Boolean>();

        DecoratedExecution inner = ctx -> {
            executed.complete(true);
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("at", ">=14 supply"), inner);
        var ctx = new StepContext(resolver);

        Thread.ofVirtual().start(() -> decorated.execute(ctx));
        Thread.sleep(100);
        assertThat(executed.isDone()).isFalse();

        counter.add(14);
        assertThat(executed.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void guardModeSkipsWhenNotMet() {
        var scope = new DefaultExecutionScope();
        scope.counter("supply");
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        DecoratedExecution inner = ctx -> Result.of(Map.of("ran", true));

        var decorated = chain.apply(
                Map.of("at", Map.of("metric", "14 supply", "mode", "guard")), inner);

        Result result = decorated.execute(new StepContext(resolver));
        assertThat(result.output()).doesNotContainKey("ran");
    }

    @Test
    void guardModeExecutesWhenMet() {
        var scope = new DefaultExecutionScope();
        var counter = scope.counter("supply");
        counter.add(20);
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        DecoratedExecution inner = ctx -> Result.of(Map.of("ran", true));

        var decorated = chain.apply(
                Map.of("at", Map.of("metric", "14 supply", "mode", "guard")), inner);

        Result result = decorated.execute(new StepContext(resolver));
        assertThat(result.output().get("ran")).isEqualTo(true);
    }

    @Test
    void compoundConditionsAllMustBeMet() throws Exception {
        var scope = new DefaultExecutionScope();
        var supply = scope.counter("supply");
        var minerals = scope.counter("minerals");
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var executed = new CompletableFuture<Boolean>();

        DecoratedExecution inner = ctx -> {
            executed.complete(true);
            return Result.of(Map.of());
        };

        var decorated = chain.apply(
                Map.of("at", List.of(">=14 supply", ">=150 minerals")), inner);

        Thread.ofVirtual().start(() -> decorated.execute(new StepContext(resolver)));

        supply.add(14);
        Thread.sleep(100);
        assertThat(executed.isDone()).isFalse();

        minerals.add(150);
        assertThat(executed.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void alreadyMetThresholdExecutesImmediately() {
        var scope = new DefaultExecutionScope();
        var counter = scope.counter("supply");
        counter.add(20);
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        var ran = new AtomicBoolean(false);
        DecoratedExecution inner = ctx -> {
            ran.set(true);
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("at", ">=14 supply"), inner);
        decorated.execute(new StepContext(resolver));
        assertThat(ran.get()).isTrue();
    }

    @Test
    void lessThanOperator() throws Exception {
        var scope = new DefaultExecutionScope();
        var counter = scope.counter("temperature");
        counter.add(30);
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var executed = new CompletableFuture<Boolean>();

        DecoratedExecution inner = ctx -> {
            executed.complete(true);
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("at", "<20 temperature"), inner);

        Thread.ofVirtual().start(() -> decorated.execute(new StepContext(resolver)));
        Thread.sleep(100);
        assertThat(executed.isDone()).isFalse();

        counter.add(-15); // 30 - 15 = 15, which is < 20
        assertThat(executed.get(2, TimeUnit.SECONDS)).isTrue();
    }
}
