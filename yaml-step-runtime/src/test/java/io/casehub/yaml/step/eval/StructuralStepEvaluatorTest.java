package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DefaultScenarioScope;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.resolver.VariableSource;
import io.casehub.yaml.core.step.MatchPattern;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.catalog.ResolvedMatchCase;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralStepEvaluatorTest {

    private StructuralStepEvaluator evaluator;
    private VariableResolver resolver;

    @BeforeEach
    void setUp() {
        var condEval = new ConditionEvaluator(null);
        evaluator = new StructuralStepEvaluator(condEval);
        resolver = new VariableResolver(Map.of(), Set.of());
    }

    private ResolvedStep leaf(String id) {
        return new ResolvedStep.InvokeStep(Map.of("id", id), Map.of());
    }

    private StepRunner successRunner(Map<String, Object> output) {
        return (step, res) -> StepResult.of(output);
    }

    private StepRunner trackingRunner(List<String> order) {
        return (step, res) -> {
            if (step instanceof ResolvedStep.InvokeStep inv) {
                order.add((String) inv.invokeSpec().get("id"));
            }
            return StepResult.of(Map.of());
        };
    }

    // ── BlockStep ──────────────────────────────────────────────────

    @Nested
    class BlockStepTests {

        @Test
        void emptyBlock_returnsSuccess() {
            var block = new ResolvedStep.BlockStep(List.of(), Map.of());
            var result = evaluator.evaluate(block, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void singleStep_delegatesToRunner() {
            var block = new ResolvedStep.BlockStep(List.of(leaf("a")), Map.of());
            var result = evaluator.evaluate(block, resolver,
                    (step, res) -> StepResult.of(Map.of("key", "value")));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("key", "value");
        }

        @Test
        void multipleSteps_executesSequentially() {
            var order = new ArrayList<String>();
            var block = new ResolvedStep.BlockStep(
                    List.of(leaf("first"), leaf("second"), leaf("third")), Map.of());
            evaluator.evaluate(block, resolver, trackingRunner(order));
            assertThat(order).containsExactly("first", "second", "third");
        }

        @Test
        void firstStepFails_shortCircuits() {
            var order = new ArrayList<String>();
            var block = new ResolvedStep.BlockStep(
                    List.of(leaf("a"), leaf("b")), Map.of());
            var result = evaluator.evaluate(block, resolver, (step, res) -> {
                if (step instanceof ResolvedStep.InvokeStep inv) {
                    order.add((String) inv.invokeSpec().get("id"));
                    if ("a".equals(inv.invokeSpec().get("id"))) {
                        return StepResult.failed("boom");
                    }
                }
                return StepResult.of(Map.of());
            });
            assertThat(result.isSuccess()).isFalse();
            assertThat(order).containsExactly("a");
        }

        @Test
        void returnsLastStepOutput() {
            var block = new ResolvedStep.BlockStep(
                    List.of(leaf("a"), leaf("b")), Map.of());
            var result = evaluator.evaluate(block, resolver, (step, res) -> {
                if (step instanceof ResolvedStep.InvokeStep inv) {
                    return StepResult.of(Map.of("from", inv.invokeSpec().get("id")));
                }
                return StepResult.of(Map.of());
            });
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("from", "b");
        }

        @Test
        void nestedBlocks_executeCorrectly() {
            var order = new ArrayList<String>();
            var inner = new ResolvedStep.BlockStep(
                    List.of(leaf("inner1"), leaf("inner2")), Map.of());
            var outer = new ResolvedStep.BlockStep(
                    List.of(leaf("outer1"), inner, leaf("outer2")), Map.of());
            evaluator.evaluate(outer, resolver, trackingRunner(order));
            assertThat(order).containsExactly("outer1", "inner1", "inner2", "outer2");
        }
    }

    // ── IfElseStep ─────────────────────────────────────────────────

    @Nested
    class IfElseStepTests {

        @Test
        void conditionTrue_executesThenBranch() {
            var step = new ResolvedStep.IfElseStep("true",
                    List.of(leaf("then")), List.of(leaf("else")), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("then");
        }

        @Test
        void conditionFalse_executesElseBranch() {
            var step = new ResolvedStep.IfElseStep("false",
                    List.of(leaf("then")), List.of(leaf("else")), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("else");
        }

        @Test
        void conditionFalse_noElseBranch_returnsSuccess() {
            var step = new ResolvedStep.IfElseStep("false",
                    List.of(leaf("then")), null, Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void conditionWithVariableInterpolation() {
            var step = new ResolvedStep.IfElseStep("${var.flag}",
                    List.of(leaf("then")), List.of(leaf("else")), Map.of());
            var flagResolver = new VariableResolver(
                    Map.of("var", (VariableSource) name -> "flag".equals(name) ? "true" : null),
                    Set.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, flagResolver, trackingRunner(order));
            assertThat(order).containsExactly("then");
        }

        @Test
        void conditionEvaluationThrows_returnsFailure() {
            var step = new ResolvedStep.IfElseStep("not-a-boolean",
                    List.of(leaf("then")), List.of(leaf("else")), Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void thenBranchFails_returnsFailure() {
            var step = new ResolvedStep.IfElseStep("true",
                    List.of(leaf("then")), null, Map.of());
            var result = evaluator.evaluate(step, resolver,
                    (s, r) -> StepResult.failed("then failed"));
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void multipleThenSteps_executesAllSequentially() {
            var step = new ResolvedStep.IfElseStep("true",
                    List.of(leaf("t1"), leaf("t2"), leaf("t3")), null, Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("t1", "t2", "t3");
        }
    }

    // ── MatchStep ──────────────────────────────────────────────────

    @Nested
    class MatchStepTests {

        @Test
        void valuePattern_matches_executesCase() {
            var mc = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("ACTIVE"), null, List.of(leaf("matched")));
            var step = new ResolvedStep.MatchStep("ACTIVE", List.of(mc), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("matched");
        }

        @Test
        void valuePattern_noMatch_returnsEmptySuccess() {
            var mc = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("ACTIVE"), null, List.of(leaf("matched")));
            var step = new ResolvedStep.MatchStep("INACTIVE", List.of(mc), Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).isEmpty();
        }

        @Test
        void structuralPattern_matches_executesCase() {
            var mc = new ResolvedMatchCase(
                    new MatchPattern.StructuralPattern(Map.of("type", "trade")),
                    null, List.of(leaf("matched")));
            var scrutinee = Map.of("type", "trade", "amount", 100);
            var step = new ResolvedStep.MatchStep("${var.event}", List.of(mc), Map.of());
            var objResolver = resolver.withObjectScope("var",
                    name -> "event".equals(name) ? scrutinee : null);
            var order = new ArrayList<String>();
            evaluator.evaluate(step, objResolver, trackingRunner(order));
            assertThat(order).containsExactly("matched");
        }

        @Test
        void anyOfPattern_matches_executesCase() {
            var mc = new ResolvedMatchCase(
                    new MatchPattern.AnyOfPattern(List.of("A", "B", "C")),
                    null, List.of(leaf("matched")));
            var step = new ResolvedStep.MatchStep("B", List.of(mc), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("matched");
        }

        @Test
        void defaultPattern_alwaysMatches() {
            var mc = new ResolvedMatchCase(
                    new MatchPattern.DefaultPattern(), null, List.of(leaf("default")));
            var step = new ResolvedStep.MatchStep("anything", List.of(mc), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("default");
        }

        @Test
        void firstMatchWins_multipleMatchingCases() {
            var c1 = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("X"), null, List.of(leaf("first")));
            var c2 = new ResolvedMatchCase(
                    new MatchPattern.DefaultPattern(), null, List.of(leaf("second")));
            var step = new ResolvedStep.MatchStep("X", List.of(c1, c2), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("first");
        }

        @Test
        void guardCondition_truthy_executesCase() {
            var condEval = new ConditionEvaluator(null);
            evaluator = new StructuralStepEvaluator(condEval);
            var mc = new ResolvedMatchCase(
                    new MatchPattern.DefaultPattern(), "true", List.of(leaf("guarded")));
            var step = new ResolvedStep.MatchStep("val", List.of(mc), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("guarded");
        }

        @Test
        void guardCondition_falsy_skipsToNextCase() {
            var c1 = new ResolvedMatchCase(
                    new MatchPattern.DefaultPattern(), "false", List.of(leaf("skipped")));
            var c2 = new ResolvedMatchCase(
                    new MatchPattern.DefaultPattern(), null, List.of(leaf("fallthrough")));
            var step = new ResolvedStep.MatchStep("val", List.of(c1, c2), Map.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("fallthrough");
        }

        @Test
        void matchContext_stringScrutinee_accessibleViaMatchPrefix() {
            var mc = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("hello"), null, List.of(leaf("check")));
            var step = new ResolvedStep.MatchStep("hello", List.of(mc), Map.of());
            var capturedResolver = new VariableResolver[1];
            evaluator.evaluate(step, resolver, (s, r) -> {
                capturedResolver[0] = r;
                return StepResult.of(Map.of());
            });
            String resolved = capturedResolver[0].resolveString("${match.value}", "test");
            assertThat(resolved).isEqualTo("hello");
        }

        @Test
        void matchContext_mapScrutinee_fieldsAccessible() {
            var scrutinee = Map.of("type", "trade", "amount", 500);
            var mc = new ResolvedMatchCase(
                    new MatchPattern.StructuralPattern(Map.of("type", "trade")),
                    null, List.of(leaf("check")));
            var step = new ResolvedStep.MatchStep("${var.event}", List.of(mc), Map.of());
            var objResolver = resolver.withObjectScope("var",
                    name -> "event".equals(name) ? scrutinee : null);
            var capturedResolver = new VariableResolver[1];
            evaluator.evaluate(step, objResolver, (s, r) -> {
                capturedResolver[0] = r;
                return StepResult.of(Map.of());
            });
            Object matchType = capturedResolver[0].resolve("${match.type}");
            assertThat(matchType).isEqualTo("trade");
        }

        @Test
        void scrutineeFromVariable_resolvesBeforeMatching() {
            var mc = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("resolved"),
                    null, List.of(leaf("matched")));
            var step = new ResolvedStep.MatchStep("${var.val}", List.of(mc), Map.of());
            var varResolver = new VariableResolver(
                    Map.of("var", (VariableSource) name ->
                            "val".equals(name) ? "resolved" : null),
                    Set.of());
            var order = new ArrayList<String>();
            evaluator.evaluate(step, varResolver, trackingRunner(order));
            assertThat(order).containsExactly("matched");
        }
    }

    // ── ParallelStep ───────────────────────────────────────────────

    @Nested
    class ParallelStepTests {

        @Test
        void emptyParallel_returnsSuccess() {
            var step = new ResolvedStep.ParallelStep(List.of(), Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void singleStep_executes() {
            var step = new ResolvedStep.ParallelStep(List.of(leaf("a")), Map.of());
            var order = new CopyOnWriteArrayList<String>();
            var result = evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv) {
                    order.add((String) inv.invokeSpec().get("id"));
                }
                return StepResult.of(Map.of("done", true));
            });
            assertThat(result.isSuccess()).isTrue();
            assertThat(order).containsExactly("a");
        }

        @Test
        void multipleSteps_executesConcurrently() throws InterruptedException {
            var latch = new CountDownLatch(2);
            var threadNames = new CopyOnWriteArrayList<String>();
            var step = new ResolvedStep.ParallelStep(
                    List.of(leaf("a"), leaf("b")), Map.of());
            evaluator.evaluate(step, resolver, (s, r) -> {
                threadNames.add(Thread.currentThread().getName());
                latch.countDown();
                try { latch.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return StepResult.of(Map.of());
            });
            assertThat(threadNames).hasSize(2);
        }

        @Test
        void allSucceed_returnsSuccess() {
            var step = new ResolvedStep.ParallelStep(
                    List.of(leaf("a"), leaf("b"), leaf("c")), Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of("ok", true)));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void oneStepFails_waitsForAll_returnsFailure() {
            var completed = new AtomicInteger(0);
            var step = new ResolvedStep.ParallelStep(
                    List.of(leaf("fail"), leaf("ok")), Map.of());
            var result = evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv
                        && "fail".equals(inv.invokeSpec().get("id"))) {
                    completed.incrementAndGet();
                    return StepResult.failed("boom");
                }
                try { Thread.sleep(50); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                completed.incrementAndGet();
                return StepResult.of(Map.of());
            });
            assertThat(result.isSuccess()).isFalse();
            assertThat(completed.get()).isEqualTo(2);
        }

        @Test
        void allFail_returnsFirstFailure() {
            var step = new ResolvedStep.ParallelStep(
                    List.of(leaf("a"), leaf("b")), Map.of());
            var result = evaluator.evaluate(step, resolver,
                    (s, r) -> StepResult.failed("fail"));
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ── TryCatchFinallyStep ─────────────────────────────────────────

    @Nested
    class TryCatchFinallyStepTests {

        @Test
        void trySucceeds_catchSkipped_finallyRuns() {
            var order = new ArrayList<String>();
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("try1")),
                    List.of(leaf("catch1")),
                    List.of(leaf("finally1")),
                    Map.of());
            evaluator.evaluate(step, resolver, trackingRunner(order));
            assertThat(order).containsExactly("try1", "finally1");
        }

        @Test
        void tryFails_catchRuns_finallyRuns() {
            var order = new ArrayList<String>();
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("try1")),
                    List.of(leaf("catch1")),
                    List.of(leaf("finally1")),
                    Map.of());
            evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv) {
                    String id = (String) inv.invokeSpec().get("id");
                    order.add(id);
                    if ("try1".equals(id)) return StepResult.failed("boom");
                }
                return StepResult.of(Map.of());
            });
            assertThat(order).containsExactly("try1", "catch1", "finally1");
        }

        @Test
        void tryFails_noCatch_finallyStillRuns() {
            var order = new ArrayList<String>();
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("try1")),
                    null,
                    List.of(leaf("finally1")),
                    Map.of());
            evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv) {
                    String id = (String) inv.invokeSpec().get("id");
                    order.add(id);
                    if ("try1".equals(id)) return StepResult.failed("boom");
                }
                return StepResult.of(Map.of());
            });
            assertThat(order).containsExactly("try1", "finally1");
        }

        @Test
        void tryFails_catchSucceeds_overallSuccess() {
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("try1")),
                    List.of(leaf("catch1")),
                    null,
                    Map.of());
            var result = evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv
                        && "try1".equals(inv.invokeSpec().get("id"))) {
                    return StepResult.failed("boom");
                }
                return StepResult.of(Map.of("handled", true));
            });
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("handled", true);
        }

        @Test
        void tryFails_noCatch_overallFailure() {
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("try1")),
                    null,
                    null,
                    Map.of());
            var result = evaluator.evaluate(step, resolver,
                    (s, r) -> StepResult.failed("boom"));
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void finallyFails_overridesTrySuccess() {
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("try1")),
                    null,
                    List.of(leaf("finally1")),
                    Map.of());
            var result = evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv
                        && "finally1".equals(inv.invokeSpec().get("id"))) {
                    return StepResult.failed("finally boom");
                }
                return StepResult.of(Map.of("ok", true));
            });
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void catchReceivesErrorContext() {
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("try1")),
                    List.of(leaf("catch1")),
                    null,
                    Map.of());
            var capturedResolver = new VariableResolver[1];
            evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv) {
                    if ("try1".equals(inv.invokeSpec().get("id"))) {
                        return StepResult.failed("original error");
                    }
                    capturedResolver[0] = r;
                }
                return StepResult.of(Map.of());
            });
            String errorMsg = capturedResolver[0].resolveString("${error.message}", "test");
            assertThat(errorMsg).isEqualTo("original error");
        }

        @Test
        void emptyTry_succeeds() {
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(), null, null, Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void multipleTrySteps_secondFails_catchRuns() {
            var order = new ArrayList<String>();
            var step = new ResolvedStep.TryCatchFinallyStep(
                    List.of(leaf("t1"), leaf("t2"), leaf("t3")),
                    List.of(leaf("c1")),
                    null,
                    Map.of());
            evaluator.evaluate(step, resolver, (s, r) -> {
                if (s instanceof ResolvedStep.InvokeStep inv) {
                    String id = (String) inv.invokeSpec().get("id");
                    order.add(id);
                    if ("t2".equals(id)) return StepResult.failed("boom");
                }
                return StepResult.of(Map.of());
            });
            assertThat(order).containsExactly("t1", "t2", "c1");
        }
    }

    // ── SelectStep ──────────────────────────────────────────────────

    @Nested
    class SelectStepTests {

        @Test
        void emptySelect_returnsSuccess() {
            var step = new ResolvedStep.SelectStep(List.of(), Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void select_withoutScope_returnsFailure() {
            var branch = new ResolvedStep.SelectBranch("wait", "sig", List.of(leaf("a")));
            var step = new ResolvedStep.SelectStep(List.of(branch), Map.of());
            var result = evaluator.evaluate(step, resolver, successRunner(Map.of()));
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void select_signalBranchWins() {
            try (var scope = new DefaultScenarioScope()) {
                var scopedEval = new StructuralStepEvaluator(
                        new ConditionEvaluator(null), scope);
                var signal = scope.signal("fast");
                signal.signal("payload");

                var branch1 = new ResolvedStep.SelectBranch("wait", "fast", List.of(leaf("winner")));
                var branch2 = new ResolvedStep.SelectBranch("wait", "slow", List.of(leaf("loser")));
                var step = new ResolvedStep.SelectStep(List.of(branch1, branch2), Map.of());

                var order = new ArrayList<String>();
                scopedEval.evaluate(step, resolver, trackingRunner(order));
                assertThat(order).containsExactly("winner");
            }
        }

        @Test
        void select_channelBranchWins() throws InterruptedException {
            try (var scope = new DefaultScenarioScope()) {
                var scopedEval = new StructuralStepEvaluator(
                        new ConditionEvaluator(null), scope);
                var channel = scope.<Object>channel("quotes");
                channel.send(Map.of("price", 42));

                var branch = new ResolvedStep.SelectBranch("subscribe", "quotes", List.of(leaf("got-quote")));
                var step = new ResolvedStep.SelectStep(List.of(branch), Map.of());

                var order = new ArrayList<String>();
                scopedEval.evaluate(step, resolver, trackingRunner(order));
                assertThat(order).containsExactly("got-quote");
            }
        }
    }

    // ── Leaf passthrough ───────────────────────────────────────────

    @Nested
    class LeafPassthroughTests {

        @Test
        void pluginStep_delegatesToRunner() {
            var plugin = new ResolvedStep.PluginStep(null, Map.of("x", 1), Map.of());
            var result = evaluator.evaluate(plugin, resolver,
                    (s, r) -> StepResult.of(Map.of("ran", true)));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("ran", true);
        }

        @Test
        void invokeStep_delegatesToRunner() {
            var invoke = new ResolvedStep.InvokeStep(Map.of("mcp", "tool"), Map.of());
            var result = evaluator.evaluate(invoke, resolver,
                    (s, r) -> StepResult.of(Map.of("ran", true)));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).containsEntry("ran", true);
        }
    }
}
