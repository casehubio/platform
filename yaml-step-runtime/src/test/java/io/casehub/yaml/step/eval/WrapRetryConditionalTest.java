package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.FailureCategory;
import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WrapRetryConditionalTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());

    @Test
    void retryOnlyMatchingCategory() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var counter = new AtomicInteger(0);

        DecoratedExecution inner = ctx -> {
            if (counter.incrementAndGet() < 3) {
                return Result.failed("timeout", FailureCategory.TIMEOUT);
            }
            return Result.of(Map.of("done", true));
        };

        var decorated = chain.apply(Map.of("retry",
                Map.of("max", 5, "on", List.of("TIMEOUT"))), inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    void noRetryOnNonMatchingCategory() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var counter = new AtomicInteger(0);

        DecoratedExecution inner = ctx -> {
            counter.incrementAndGet();
            return Result.failed("permanent", FailureCategory.PERMANENT);
        };

        var decorated = chain.apply(Map.of("retry",
                Map.of("max", 5, "on", List.of("TIMEOUT"))), inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isFalse();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void nullCategoryAlwaysRetried() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var counter = new AtomicInteger(0);

        DecoratedExecution inner = ctx -> {
            if (counter.incrementAndGet() < 2) {
                return Result.failed("generic error");
            }
            return Result.of(Map.of("done", true));
        };

        var decorated = chain.apply(Map.of("retry",
                Map.of("max", 5, "on", List.of("TIMEOUT"))), inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isTrue();
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void withoutOnFieldRetriesAll() {
        var chain = new DecoratorChain(new ConditionEvaluator(null), SpeedMultiplier.identity());
        var counter = new AtomicInteger(0);

        DecoratedExecution inner = ctx -> {
            if (counter.incrementAndGet() < 3) {
                return Result.failed("permanent", FailureCategory.PERMANENT);
            }
            return Result.of(Map.of("done", true));
        };

        var decorated = chain.apply(Map.of("retry", 5), inner);
        Result result = decorated.execute(new StepContext(resolver));

        assertThat(result.isSuccess()).isTrue();
        assertThat(counter.get()).isEqualTo(3);
    }
}
