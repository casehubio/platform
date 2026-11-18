package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DefaultExecutionScope;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WrapBackgroundDecoratorTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void backgroundReturnsImmediately() {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        var blocker = new CountDownLatch(1);
        DecoratedExecution inner = ctx -> {
            try { blocker.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("background", true), inner);
        long start = System.currentTimeMillis();
        Result result = decorated.execute(new StepContext(resolver));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.isSuccess()).isTrue();
        assertThat(elapsed).isLessThan(1000);
    }

    @Test
    void backgroundTaskRunsInScope() throws Exception {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var executed = new CompletableFuture<Boolean>();

        DecoratedExecution inner = ctx -> {
            executed.complete(true);
            return Result.of(Map.of());
        };

        chain.apply(Map.of("background", true), inner).execute(new StepContext(resolver));
        assertThat(executed.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void backgroundTaskCancelledOnScopeClose() throws Exception {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var interrupted = new CompletableFuture<Boolean>();

        var blocker = new CountDownLatch(1);
        DecoratedExecution inner = ctx -> {
            try { blocker.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) {
                interrupted.complete(true);
            }
            return Result.of(Map.of());
        };

        chain.apply(Map.of("background", true), inner).execute(new StepContext(resolver));
        Thread.sleep(50);
        scope.close();

        assertThat(interrupted.get(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void backgroundComposesWithContinuousLoop() throws Exception {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var counter = scope.counter("bg-count");
        var started = new CountDownLatch(3);

        DecoratedExecution inner = ctx -> {
            counter.increment();
            started.countDown();
            try { Thread.sleep(20); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("interrupted");
            }
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("background", true, "loop", "continuous"), inner);
        long start = System.currentTimeMillis();
        Result result = decorated.execute(new StepContext(resolver));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.isSuccess()).isTrue();
        assertThat(elapsed).isLessThan(500); // returned immediately

        started.await(2, TimeUnit.SECONDS);
        assertThat(counter.get()).isGreaterThanOrEqualTo(3);

        scope.close(); // cleanup
    }

    @Test
    void withoutBackgroundRunsNormally() {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        DecoratedExecution inner = ctx -> Result.of(Map.of("ran", true));

        var decorated = chain.apply(Map.of("background", false), inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output().get("ran")).isEqualTo(true);
    }

    @Test
    void backgroundFailureCallsListenerOnBackgroundFailure() throws Exception {
        var scope           = new DefaultExecutionScope();
        var failureReceived = new CompletableFuture<Result>();
        var nameReceived    = new CompletableFuture<String>();

        StepExecutionListener listener = new StepExecutionListener() {
            @Override
            public void beforeStep(io.casehub.yaml.step.catalog.ResolvedStep step) {}

            @Override
            public void afterStep(io.casehub.yaml.step.catalog.ResolvedStep step, Result result, Map<String, Object> detail) {}

            @Override
            public void onBackgroundFailure(String stepName, Result result) {
                nameReceived.complete(stepName);
                failureReceived.complete(result);
            }
        };

        var evaluator = new StructuralStepEvaluator(new ConditionEvaluator(null), scope)
                                .withListener(listener);

        var step = new io.casehub.yaml.step.catalog.ResolvedStep.PluginStep(
                "bg-step", "test-action", Map.of(), Map.of("background", true));

        evaluator.evaluate(step, resolver,
                           (s, r) -> Result.failed("simulated failure"));

        var result = failureReceived.get(2, TimeUnit.SECONDS);
        assertThat(result.isSuccess()).isFalse();
        assertThat(nameReceived.get(2, TimeUnit.SECONDS)).isEqualTo("bg-step");
        scope.close();
    }

    @Test
    void backgroundUsesStepNameFromContext() throws Exception {
        var                                           scope       = new DefaultExecutionScope();
        var                                           failureName = new CompletableFuture<String>();
        java.util.function.BiConsumer<String, Result> handler     = (name, result) -> failureName.complete(name);

        var config = new DecoratorConfig(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope,
                                         null, null, null, null, handler);
        var chain = new DecoratorChain(config);

        DecoratedExecution inner     = ctx -> Result.failed("simulated failure");
        var                decorated = chain.apply(Map.of("background", true), inner);
        decorated.execute(new StepContext(resolver).withStepName("poll-queue"));

        assertThat(failureName.get(2, TimeUnit.SECONDS)).isEqualTo("poll-queue");
        scope.close();
    }

    @Test
    void backgroundFallsBackToDefaultNameWhenNoStepName() throws Exception {
        var                                           scope       = new DefaultExecutionScope();
        var                                           failureName = new CompletableFuture<String>();
        java.util.function.BiConsumer<String, Result> handler     = (name, result) -> failureName.complete(name);

        var config = new DecoratorConfig(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope,
                                         null, null, null, null, handler);
        var chain = new DecoratorChain(config);

        DecoratedExecution inner     = ctx -> Result.failed("simulated failure");
        var                decorated = chain.apply(Map.of("background", true), inner);
        decorated.execute(new StepContext(resolver));

        assertThat(failureName.get(2, TimeUnit.SECONDS)).isEqualTo("background");
        scope.close();
    }


}
