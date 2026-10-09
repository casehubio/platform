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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WrapCancelDecoratorTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void cancelInterruptsRunningStep() throws Exception {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var started = new CountDownLatch(1);
        var result = new CompletableFuture<Result>();

        DecoratedExecution inner = ctx -> {
            started.countDown();
            try { Thread.sleep(5000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("interrupted");
            }
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("cancel", "stop-signal"), inner);
        Thread.ofVirtual().start(() -> result.complete(decorated.execute(new StepContext(resolver))));

        started.await(1, TimeUnit.SECONDS);
        Thread.sleep(50);
        scope.signal("stop-signal").signal();

        Result r = result.get(2, TimeUnit.SECONDS);
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    void cancelOnAlreadySignalledFiresImmediately() {
        var scope = new DefaultExecutionScope();
        scope.signal("already-done").signal();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        DecoratedExecution inner = ctx -> {
            try { Thread.sleep(5000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("interrupted");
            }
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("cancel", "already-done"), inner);
        long start = System.currentTimeMillis();
        Result r = decorated.execute(new StepContext(resolver));
        long elapsed = System.currentTimeMillis() - start;

        assertThat(r.isSuccess()).isFalse();
        assertThat(elapsed).isLessThan(2000);
    }

    @Test
    void cancelComposesWithContinuousLoop() throws Exception {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var iterations = new AtomicInteger(0);
        var result = new CompletableFuture<Result>();

        DecoratedExecution inner = ctx -> {
            iterations.incrementAndGet();
            try { Thread.sleep(20); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("interrupted");
            }
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("loop", "continuous", "cancel", "stop"), inner);
        Thread.ofVirtual().start(() -> result.complete(decorated.execute(new StepContext(resolver))));

        Thread.sleep(200);
        scope.signal("stop").signal();

        result.get(2, TimeUnit.SECONDS);
        assertThat(iterations.get()).isGreaterThan(2);
    }

    @Test
    void normalCompletionWithoutCancelSignal() {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        DecoratedExecution inner = ctx -> Result.of(Map.of("done", true));

        var decorated = chain.apply(Map.of("cancel", "never-fires"), inner);
        Result r = decorated.execute(new StepContext(resolver));

        assertThat(r.isSuccess()).isTrue();
        assertThat(r.output().get("done")).isEqualTo(true);
    }
}
