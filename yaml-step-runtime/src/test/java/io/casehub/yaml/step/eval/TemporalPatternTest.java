package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DefaultExecutionScope;
import io.casehub.yaml.core.orchestration.DefaultPrimitiveFactory;
import io.casehub.yaml.core.orchestration.OrcChannel;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.runtime.SpeedMultiplier;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalPatternTest {

    private final VariableResolver resolver = new VariableResolver(Map.of(), Set.of());
    private final StepRunner noOpRunner = (step, r) -> Result.of(Map.of());

    private ConditionEvaluator conditionEvaluator() {
        return new ConditionEvaluator(expr -> {
            if (expr.contains("==")) {
                String[] parts = expr.split("==");
                return parts.length == 2 && parts[0].trim().equals(parts[1].trim());
            }
            return "true".equals(expr);
        });
    }

    @Test
    void timedAbsenceDetected_timeoutFires() {
        try (var scope = new DefaultExecutionScope(new DefaultPrimitiveFactory(() -> 100.0),
                () -> 100.0)) {
            var condEval = conditionEvaluator();
            var evaluator = new StructuralStepEvaluator(condEval, scope);

            var events = scope.<Object>channel("events");
            events.send(Map.of("type", "EXPANSION"));

            var fromA = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "events")));
            var fromB = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "events")));
            var timedFromB = new ResolvedStep.BlockStep(null, List.of(fromB),
                    Map.of("timeout", "200ms", "on-error", "greedy-detected"));

            var pattern = new ResolvedStep.BlockStep(null, List.of(fromA, timedFromB), Map.of());
            Result result = evaluator.evaluate(pattern, resolver, noOpRunner);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output().get("on-error.fallback")).isEqualTo("greedy-detected");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void timedAbsenceNotDetected_eventArrives() {
        try (var scope = new DefaultExecutionScope(new DefaultPrimitiveFactory(() -> 100.0),
                () -> 100.0)) {
            var condEval = conditionEvaluator();
            var evaluator = new StructuralStepEvaluator(condEval, scope);

            var events = scope.<Object>channel("events");
            events.send(Map.of("type", "EXPANSION"));
            events.send(Map.of("type", "DEFENSE"));

            var fromA = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "events")));
            var fromB = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "events")));
            var timedFromB = new ResolvedStep.BlockStep(null, List.of(fromB),
                    Map.of("timeout", "2s"));

            var pattern = new ResolvedStep.BlockStep(null, List.of(fromA, timedFromB), Map.of());
            Result result = evaluator.evaluate(pattern, resolver, noOpRunner);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output()).doesNotContainKey("on-error.fallback");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void periodicHeartbeat_missingHeartbeatDetected() {
        try (var scope = new DefaultExecutionScope(new DefaultPrimitiveFactory(() -> 100.0),
                () -> 100.0)) {
            var condEval = conditionEvaluator();
            var evaluator = new StructuralStepEvaluator(condEval, scope);

            var fromHeartbeat = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "heartbeats")));
            var timedHeartbeat = new ResolvedStep.BlockStep(null, List.of(fromHeartbeat),
                    Map.of("timeout", "200ms", "on-error", "unresponsive"));

            Result result = evaluator.evaluate(timedHeartbeat, resolver, noOpRunner);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.output().get("on-error.fallback")).isEqualTo("unresponsive");
        }
    }
}
