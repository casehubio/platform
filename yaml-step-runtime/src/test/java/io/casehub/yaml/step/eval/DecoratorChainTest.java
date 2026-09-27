package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DefaultScenarioScope;
import io.casehub.yaml.core.orchestration.ScenarioScope;
import io.casehub.yaml.core.resolver.ObjectVariableSource;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.VariableSource;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.StepResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DecoratorChainTest {

    private DecoratorChain chain;
    private VariableResolver resolver;

    @BeforeEach
    void setUp() {
        var condEval = new ConditionEvaluator(null);
        chain = new DecoratorChain(condEval, SpeedMultiplier.identity());
        resolver = new VariableResolver(Map.of(), Set.of());
    }

    private DecoratedExecution success(Map<String, Object> output) {
        return r -> StepResult.of(output);
    }

    private DecoratedExecution failure(String msg) {
        return r -> StepResult.failed(msg);
    }

    private DecoratedExecution counting(AtomicInteger counter) {
        return r -> {
            counter.incrementAndGet();
            return StepResult.of(Map.of());
        };
    }

    // ── when (position 1) ──────────────────────────────────────────

    @Nested
    class WhenTests {

        @Test
        void whenTrue_executesInner() {
            var decorators = Map.<String, Object>of("when", "true");
            var result = chain.apply(decorators, success(Map.of("ran", true)))
                    .execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("ran", true);
        }

        @Test
        void whenFalse_skipsInner() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("when", "false");
            var result = chain.apply(decorators, counting(count)).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isZero();
        }

        @Test
        void whenWithVariableResolution() {
            var flagResolver = new VariableResolver(
                    Map.of("var", (VariableSource) name ->
                            "flag".equals(name) ? "yes" : null),
                    Set.of());
            var decorators = Map.<String, Object>of("when", "${var.flag}");
            var result = chain.apply(decorators, success(Map.of("ran", true)))
                    .execute(flagResolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("ran", true);
        }

        @Test
        void whenEvaluationFails_returnsFailure() {
            var decorators = Map.<String, Object>of("when", "not-a-boolean");
            var result = chain.apply(decorators, success(Map.of())).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── loop (position 3) ──────────────────────────────────────────

    @Nested
    class LoopTests {

        @Test
        void countLoop_executesExactlyNTimes() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("loop", 5);
            chain.apply(decorators, counting(count)).execute(resolver);
            assertThat(count.get()).isEqualTo(5);
        }

        @Test
        void countLoop_mapForm() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("loop", Map.of("count", 3));
            chain.apply(decorators, counting(count)).execute(resolver);
            assertThat(count.get()).isEqualTo(3);
        }

        @Test
        void untilLoop_exitsWhenConditionMet() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("loop",
                    Map.of("until", "${var.done}", "count", 10));
            var result = chain.apply(decorators, r -> {
                int n = count.incrementAndGet();
                return StepResult.of(Map.of());
            }).execute(new VariableResolver(
                    Map.of("var", (VariableSource) name ->
                            "done".equals(name) ? String.valueOf(count.get() >= 3) : null),
                    Set.of()));
            assertThat(count.get()).isEqualTo(3);
        }

        @Test
        void loop_innerFailure_stopsLoop() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("loop", 5);
            var result = chain.apply(decorators, r -> {
                if (count.incrementAndGet() == 3) return StepResult.failed("boom");
                return StepResult.of(Map.of());
            }).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
            assertThat(count.get()).isEqualTo(3);
        }

        @Test
        void loop_returnsLastIterationOutput() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("loop", 3);
            var result = chain.apply(decorators, r -> {
                int n = count.incrementAndGet();
                return StepResult.of(Map.of("iteration", n));
            }).execute(resolver);
            assertThat(result.output()).containsEntry("iteration", 3);
        }
    }

    // ── on-error (position 4) ──────────────────────────────────────

    @Nested
    class OnErrorTests {

        @Test
        void noError_passesThrough() {
            var decorators = Map.<String, Object>of("on-error", "fallback");
            var result = chain.apply(decorators, success(Map.of("ok", true)))
                    .execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("ok", true);
        }

        @Test
        void innerFailure_returnsSuccessWithFallbackInfo() {
            var decorators = Map.<String, Object>of("on-error", "fallback");
            var result = chain.apply(decorators, failure("boom"))
                    .execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("on-error.fallback", "fallback");
            assertThat(result.output()).containsEntry("on-error.caught", "boom");
        }

        @Test
        void innerException_caughtWithFallbackInfo() {
            var decorators = Map.<String, Object>of("on-error", "fallback");
            var result = chain.apply(decorators, r -> {
                throw new RuntimeException("unexpected");
            }).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("on-error.fallback", "fallback");
        }
    }

    // ── timeout (position 5) ───────────────────────────────────────

    @Nested
    class TimeoutTests {

        @Test
        void completesBeforeDeadline_succeeds() {
            var decorators = Map.<String, Object>of("timeout", "5s");
            var result = chain.apply(decorators, success(Map.of("ok", true)))
                    .execute(resolver);
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void exceedsDeadline_returnsFailure() {
            var decorators = Map.<String, Object>of("timeout", "50ms");
            var result = chain.apply(decorators, r -> {
                try { Thread.sleep(200); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return StepResult.of(Map.of());
            }).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── retry (position 7) ─────────────────────────────────────────

    @Nested
    class RetryTests {

        @Test
        void succeedsOnFirst_noRetry() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("retry", 3);
            var result = chain.apply(decorators, r -> {
                count.incrementAndGet();
                return StepResult.of(Map.of("ok", true));
            }).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isEqualTo(1);
        }

        @Test
        void failsThenSucceeds_retriesCorrectly() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("retry", 3);
            var result = chain.apply(decorators, r -> {
                if (count.incrementAndGet() < 3) return StepResult.failed("not yet");
                return StepResult.of(Map.of("ok", true));
            }).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isEqualTo(3);
        }

        @Test
        void exhaustsRetries_returnsLastFailure() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("retry", 2);
            var result = chain.apply(decorators, r -> {
                count.incrementAndGet();
                return StepResult.failed("still failing");
            }).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
            assertThat(count.get()).isEqualTo(2);
        }

        @Test
        void retryWithBackoff_mapForm() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("retry",
                    Map.of("max", 3, "backoff", "fixed", "delay", "10ms"));
            var result = chain.apply(decorators, r -> {
                if (count.incrementAndGet() < 3) return StepResult.failed("not yet");
                return StepResult.of(Map.of("ok", true));
            }).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isEqualTo(3);
        }

        @Test
        void retryWithException_catchesAndRetries() {
            var count = new AtomicInteger(0);
            var decorators = Map.<String, Object>of("retry", 3);
            var result = chain.apply(decorators, r -> {
                if (count.incrementAndGet() < 3) throw new RuntimeException("transient");
                return StepResult.of(Map.of("ok", true));
            }).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isEqualTo(3);
        }
    }

    // ── delay (position 9) ─────────────────────────────────────────

    @Nested
    class DelayTests {

        @Test
        void delay_waitsBeforeExecution() {
            var decorators = Map.<String, Object>of("delay", "50ms");
            long start = System.nanoTime();
            chain.apply(decorators, success(Map.of())).execute(resolver);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            assertThat(elapsed).isGreaterThanOrEqualTo(40);
        }

        @Test
        void delay_respectsSpeedMultiplier() {
            var fastChain = new DecoratorChain(
                    new ConditionEvaluator(null), () -> 10.0);
            var decorators = Map.<String, Object>of("delay", "500ms");
            long start = System.nanoTime();
            fastChain.apply(decorators, success(Map.of())).execute(resolver);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            assertThat(elapsed).isLessThan(200);
        }
    }

    // ── transform (position 13) ────────────────────────────────────

    @Nested
    class TransformTests {

        @Test
        void transform_notAppliedOnFailure() {
            var decorators = Map.<String, Object>of("transform", "ignored");
            var result = chain.apply(decorators, failure("boom")).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── composition ────────────────────────────────────────────────

    @Nested
    class CompositionTests {

        @Test
        void whenFalse_skipsEntireChain() {
            var count = new AtomicInteger(0);
            var decorators = new LinkedHashMap<String, Object>();
            decorators.put("when", "false");
            decorators.put("retry", 3);
            var result = chain.apply(decorators, counting(count)).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isZero();
        }

        @Test
        void retryInsideLoop_retriesPerIteration() {
            var totalCalls = new AtomicInteger(0);
            var decorators = new LinkedHashMap<String, Object>();
            decorators.put("loop", 2);
            decorators.put("retry", 2);
            var result = chain.apply(decorators, r -> {
                int n = totalCalls.incrementAndGet();
                if (n % 2 == 1) return StepResult.failed("transient");
                return StepResult.of(Map.of());
            }).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(totalCalls.get()).isEqualTo(4);
        }

        @Test
        void onError_catchesTimeoutException() {
            var decorators = new LinkedHashMap<String, Object>();
            decorators.put("on-error", "fallback");
            decorators.put("timeout", "50ms");
            var result = chain.apply(decorators, r -> {
                try { Thread.sleep(200); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return StepResult.of(Map.of());
            }).execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("on-error.fallback", "fallback");
        }

        @Test
        void noDecorators_passesThrough() {
            var result = chain.apply(Map.of(), success(Map.of("ok", true)))
                    .execute(resolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("ok", true);
        }
    }

    // ── forEach (position 2) ───────────────────────────────────────

    @Nested
    class ForEachTests {

        @Test
        void sequential_iteratesOverList() {
            var items = List.of(
                    Map.of("name", "a"),
                    Map.of("name", "b"),
                    Map.of("name", "c"));
            var listResolver = resolver.withObjectScope("var",
                    name -> "items".equals(name) ? items : null);
            var decorators = Map.<String, Object>of("forEach",
                    Map.of("in", "${var.items}", "as", "item"));
            var seen = new ArrayList<String>();
            chain.apply(decorators, r -> {
                Object idx = r.resolve("${each.index}");
                seen.add(String.valueOf(idx));
                return StepResult.of(Map.of());
            }).execute(listResolver);
            assertThat(seen).containsExactly("0", "1", "2");
        }

        @Test
        void emptyCollection_skips() {
            var listResolver = resolver.withObjectScope("var",
                    name -> "items".equals(name) ? List.of() : null);
            var decorators = Map.<String, Object>of("forEach",
                    Map.of("in", "${var.items}", "as", "item"));
            var count = new AtomicInteger(0);
            var result = chain.apply(decorators, counting(count)).execute(listResolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isZero();
        }

        @Test
        void parallel_executesConcurrently() {
            var items = List.of("a", "b", "c");
            var listResolver = resolver.withObjectScope("var",
                    name -> "items".equals(name) ? items : null);
            var decorators = Map.<String, Object>of("forEach",
                    Map.of("in", "${var.items}", "as", "item", "parallel", true));
            var count = new AtomicInteger(0);
            var result = chain.apply(decorators, r -> {
                count.incrementAndGet();
                return StepResult.of(Map.of());
            }).execute(listResolver);
            assertThat(result.isSuccess()).isTrue();
            assertThat(count.get()).isEqualTo(3);
        }

        @Test
        void failureInOneIteration_stopsSequential() {
            var items = List.of("a", "b", "c");
            var listResolver = resolver.withObjectScope("var",
                    name -> "items".equals(name) ? items : null);
            var decorators = Map.<String, Object>of("forEach",
                    Map.of("in", "${var.items}", "as", "item"));
            var count = new AtomicInteger(0);
            var result = chain.apply(decorators, r -> {
                if (count.incrementAndGet() == 2) return StepResult.failed("boom");
                return StepResult.of(Map.of());
            }).execute(listResolver);
            assertThat(result.isSuccess()).isFalse();
            assertThat(count.get()).isEqualTo(2);
        }
    }

    // ── semaphore (position 8) ─────────────────────────────────────

    @Nested
    class SemaphoreTests {

        @Test
        void semaphore_acquiresAndReleases() {
            try (var scope = new DefaultScenarioScope()) {
                var scopedChain = new DecoratorChain(
                        new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
                var decorators = Map.<String, Object>of("semaphore",
                        Map.of("name", "rate", "permits", 2));
                var result = scopedChain.apply(decorators, success(Map.of("ok", true)))
                        .execute(resolver);
                assertThat(result.isSuccess()).isTrue();
                assertThat(scope.semaphore("rate", 2).availablePermits()).isEqualTo(2);
            }
        }

        @Test
        void mutex_sugar_equivalentToPermitsOne() {
            try (var scope = new DefaultScenarioScope()) {
                var scopedChain = new DecoratorChain(
                        new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
                var decorators = Map.<String, Object>of("mutex", "exclusive");
                var result = scopedChain.apply(decorators, success(Map.of("ok", true)))
                        .execute(resolver);
                assertThat(result.isSuccess()).isTrue();
            }
        }

        @Test
        void semaphore_releasesOnFailure() {
            try (var scope = new DefaultScenarioScope()) {
                var scopedChain = new DecoratorChain(
                        new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
                var decorators = Map.<String, Object>of("semaphore",
                        Map.of("name", "lock", "permits", 1));
                scopedChain.apply(decorators, failure("boom")).execute(resolver);
                assertThat(scope.semaphore("lock", 1).availablePermits()).isEqualTo(1);
            }
        }

        @Test
        void semaphore_withoutScope_returnsFailure() {
            var decorators = Map.<String, Object>of("semaphore",
                    Map.of("name", "rate", "permits", 2));
            var result = chain.apply(decorators, success(Map.of())).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── wait (position 6) ──────────────────────────────────────────

    @Nested
    class WaitTests {

        @Test
        void wait_blocksUntilSignalled() throws InterruptedException {
            try (var scope = new DefaultScenarioScope()) {
                var scopedChain = new DecoratorChain(
                        new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
                var decorators = Map.<String, Object>of("wait", "ready");
                var signal = scope.signal("ready");
                signal.signal("payload-data");
                var result = scopedChain.apply(decorators, success(Map.of("ok", true)))
                        .execute(resolver);
                assertThat(result.isSuccess()).isTrue();
            }
        }

        @Test
        void wait_withoutScope_returnsFailure() {
            var decorators = Map.<String, Object>of("wait", "ready");
            var result = chain.apply(decorators, success(Map.of())).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── signal (position 11) ───────────────────────────────────────

    @Nested
    class SignalTests {

        @Test
        void signal_firesAfterAction() {
            try (var scope = new DefaultScenarioScope()) {
                var scopedChain = new DecoratorChain(
                        new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
                var decorators = Map.<String, Object>of("signal", "done");
                scopedChain.apply(decorators, success(Map.of("result", "data")))
                        .execute(resolver);
                var signal = scope.signal("done");
                assertThat(signal.isSignalled()).isTrue();
                assertThat(signal.payload()).isInstanceOf(Map.class);
            }
        }

        @Test
        void signal_notFiredOnFailure() {
            try (var scope = new DefaultScenarioScope()) {
                var scopedChain = new DecoratorChain(
                        new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
                var decorators = Map.<String, Object>of("signal", "done");
                scopedChain.apply(decorators, failure("boom")).execute(resolver);
                var signal = scope.signal("done");
                assertThat(signal.isSignalled()).isFalse();
            }
        }
    }

    // ── publish (position 11) ──────────────────────────────────────

    @Nested
    class PublishTests {

        @Test
        void publish_sendsToChannel() throws InterruptedException {
            try (var scope = new DefaultScenarioScope()) {
                var scopedChain = new DecoratorChain(
                        new ConditionEvaluator(null), SpeedMultiplier.identity(), scope);
                var decorators = Map.<String, Object>of("publish",
                        Map.of("channel", "events"));
                scopedChain.apply(decorators, success(Map.of("event", "data")))
                        .execute(resolver);
                var channel = scope.<Map<String, Object>>channel("events");
                assertThat(channel.isEmpty()).isFalse();
            }
        }
    }

    // ── transition (position 12) ───────────────────────────────────

    @Nested
    class TransitionTests {

        @Test
        void transition_withoutScope_returnsFailure() {
            var decorators = Map.<String, Object>of("transition",
                    Map.of("machine", "workflow", "event", "approve"));
            var result = chain.apply(decorators, success(Map.of())).execute(resolver);
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
