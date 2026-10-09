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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WrapLoopContinuousTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void continuousLoopRunsUntilInterrupted() throws Exception {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var counter = new AtomicInteger(0);
        var result = new CompletableFuture<Result>();

        DecoratedExecution inner = ctx -> {
            counter.incrementAndGet();
            try { Thread.sleep(10); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Result.failed("interrupted");
            }
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("loop", "continuous"), inner);
        Thread t = Thread.ofVirtual().start(() -> result.complete(decorated.execute(new StepContext(resolver))));

        Thread.sleep(200);
        t.interrupt();

        Result r = result.get(2, TimeUnit.SECONDS);
        assertThat(counter.get()).isGreaterThan(3);
    }

    @Test
    void continuousLoopStopsOnFailure() {
        var scope = new DefaultExecutionScope();
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
        var counter = new AtomicInteger(0);

        DecoratedExecution inner = ctx -> {
            if (counter.incrementAndGet() >= 3) {
                return Result.failed("stop");
            }
            return Result.of(Map.of());
        };

        var decorated = chain.apply(Map.of("loop", "continuous"), inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isFalse();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void continuousLoopWithUntilConditionInMap() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var counter = new AtomicInteger(0);

        DecoratedExecution inner = ctx -> {
            counter.incrementAndGet();
            return Result.of(Map.of());
        };

        // continuous: true + until: condition → loops until condition, no max
        var decorated = chain.apply(Map.of("loop", Map.of("until", "true")), inner);
        decorated.execute(new StepContext(resolver));

        assertThat(counter.get()).isEqualTo(1); // condition true immediately
    }
}
