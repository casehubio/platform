package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.DefaultExecutionScope;
import io.casehub.yaml.core.orchestration.DefaultPrimitiveFactory;
import io.casehub.yaml.core.orchestration.OrcChannel;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.core.step.MatchPattern;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedMatchCase;
import io.casehub.yaml.step.catalog.ResolvedStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RecursiveCompositionTest {

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
    void raceWithIndependentChannels() throws Exception {
        try (var scope = new DefaultExecutionScope()) {
            var condEval = conditionEvaluator();
            var evaluator = new StructuralStepEvaluator(condEval, scope);

            var mfaChannel = scope.<Object>channel("mfa");
            var ssoChannel = scope.<Object>channel("sso");

            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(50);
                    ssoChannel.send(Map.of("token", "sso-123"));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            var mfaBranch = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "mfa")));
            var ssoBranch = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "sso")));

            var race = new ResolvedStep.RaceStep(null, List.of(mfaBranch, ssoBranch), Map.of());
            Result result = evaluator.evaluate(race, resolver, noOpRunner);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    @Test
    void sequentialFromWithMatchCases() throws Exception {
        try (var scope = new DefaultExecutionScope()) {
            var condEval = conditionEvaluator();
            var evaluator = new StructuralStepEvaluator(condEval, scope);

            var events = scope.<Object>channel("events");
            var matched = scope.<Object>channel("matched");

            events.send(Map.of("type", "A"));
            events.send(Map.of("type", "B"));

            var fromA = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "events")));

            var publishAB = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("publish", Map.of("channel", "matched", "data", "AB")));
            var publishAC = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("publish", Map.of("channel", "matched", "data", "AC")));

            var caseB = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("B"), null, List.of(publishAB));
            var caseC = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("C"), null, List.of(publishAC));

            var matchStep = new ResolvedStep.MatchStep(null, "${from.type}",
                    List.of(caseB, caseC),
                    Map.of("from", Map.of("channel", "events")));

            var block = new ResolvedStep.BlockStep(null, List.of(fromA, matchStep), Map.of());
            Result result = evaluator.evaluate(block, resolver, noOpRunner);

            assertThat(result.isSuccess()).isTrue();
            Object matchedItem = matched.receive(100, TimeUnit.MILLISECONDS);
            assertThat(matchedItem).isEqualTo("AB");
        }
    }

    @Test
    void loopedSequentialPattern() throws Exception {
        try (var scope = new DefaultExecutionScope()) {
            var condEval = conditionEvaluator();
            var evaluator = new StructuralStepEvaluator(condEval, scope);

            var events = scope.<Object>channel("events");
            var matched = scope.<Object>channel("matched");

            for (String type : List.of("A", "B", "A", "C", "A", "B")) {
                events.send(Map.of("type", type));
            }

            var fromA = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("from", Map.of("channel", "events")));

            var publishAB = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("publish", Map.of("channel", "matched", "data", "AB")));
            var publishAC = new ResolvedStep.BlockStep(null, List.of(),
                    Map.of("publish", Map.of("channel", "matched", "data", "AC")));

            var caseB = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("B"), null, List.of(publishAB));
            var caseC = new ResolvedMatchCase(
                    new MatchPattern.ValuePattern("C"), null, List.of(publishAC));

            var matchStep = new ResolvedStep.MatchStep(null, "${from.type}",
                    List.of(caseB, caseC),
                    Map.of("from", Map.of("channel", "events")));

            var block = new ResolvedStep.BlockStep(null, List.of(fromA, matchStep), Map.of());
            var loop = new ResolvedStep.BlockStep(null, List.of(block),
                    Map.of("loop", 3));

            Result result = evaluator.evaluate(loop, resolver, noOpRunner);

            assertThat(result.isSuccess()).isTrue();
            assertThat(matched.receive(100, TimeUnit.MILLISECONDS)).isEqualTo("AB");
            assertThat(matched.receive(100, TimeUnit.MILLISECONDS)).isEqualTo("AC");
            assertThat(matched.receive(100, TimeUnit.MILLISECONDS)).isEqualTo("AB");
            assertThat(matched.isEmpty()).isTrue();
        }
    }
}
