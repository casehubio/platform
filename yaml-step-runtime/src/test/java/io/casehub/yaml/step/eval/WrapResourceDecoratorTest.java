package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DefaultExecutionScope;
import io.casehub.yaml.core.orchestration.Priority;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WrapResourceDecoratorTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void resourceAcquiresAndReleases() {
        var scope = new DefaultExecutionScope();
        scope.prioritySemaphore("minerals", 1);
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        DecoratedExecution inner = ctx -> Result.of(Map.of("ran", true));
        var decorated = chain.apply(Map.of("resource", "minerals"), inner);

        Result result = decorated.execute(new StepContext(resolver));
        assertThat(result.output().get("ran")).isEqualTo(true);
        assertThat(scope.prioritySemaphore("minerals", 1).availablePermits()).isEqualTo(1);
    }

    @Test
    void highPriorityAcquiresBeforeBackground() throws Exception {
        var scope = new DefaultExecutionScope();
        var sem = scope.prioritySemaphore("minerals", 1);
        sem.acquire(Priority.NORMAL); // hold
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        var order = Collections.synchronizedList(new ArrayList<String>());
        var ready = new CountDownLatch(2);

        Thread bg = Thread.ofVirtual().start(() -> {
            var dec = chain.apply(
                    Map.of("resource", "minerals", "priority", "background"),
                    ctx -> { order.add("bg"); return Result.of(Map.of()); });
            ready.countDown();
            dec.execute(new StepContext(resolver));
        });

        Thread hi = Thread.ofVirtual().start(() -> {
            var dec = chain.apply(
                    Map.of("resource", "minerals", "priority", "high"),
                    ctx -> { order.add("high"); return Result.of(Map.of()); });
            ready.countDown();
            dec.execute(new StepContext(resolver));
        });

        ready.await(1, TimeUnit.SECONDS);
        Thread.sleep(100); // let both enqueue
        sem.release();

        bg.join(2000);
        hi.join(2000);

        assertThat(order).containsExactly("high", "bg");
    }

    @Test
    void defaultPriorityIsNormal() throws Exception {
        var scope = new DefaultExecutionScope();
        var sem = scope.prioritySemaphore("minerals", 1);
        sem.acquire(Priority.NORMAL); // hold
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        var executed = new CountDownLatch(1);
        Thread t = Thread.ofVirtual().start(() -> {
            var dec = chain.apply(Map.of("resource", "minerals"),
                    ctx -> { executed.countDown(); return Result.of(Map.of()); });
            dec.execute(new StepContext(resolver));
        });

        Thread.sleep(50);
        assertThat(executed.getCount()).isEqualTo(1); // blocked
        sem.release();
        assertThat(executed.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void releaseOnFailure() {
        var scope = new DefaultExecutionScope();
        scope.prioritySemaphore("minerals", 1);
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);

        DecoratedExecution inner = ctx -> { throw new RuntimeException("boom"); };
        var decorated = chain.apply(Map.of("resource", "minerals"), inner);

        try {
            decorated.execute(new StepContext(resolver));
        } catch (RuntimeException e) {
            // expected
        }
        assertThat(scope.prioritySemaphore("minerals", 1).availablePermits()).isEqualTo(1);
    }
}
