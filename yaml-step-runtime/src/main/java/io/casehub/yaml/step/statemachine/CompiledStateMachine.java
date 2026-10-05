package io.casehub.yaml.step.statemachine;

import io.casehub.yaml.core.orchestration.OrcStateMachine;
import io.casehub.yaml.core.orchestration.ExecutionScope;
import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.eval.StepRunner;

import java.util.List;
import java.util.Map;

public record CompiledStateMachine(
        OrcStateMachine<String> stateMachine,
        ExecutionScope scope,
        StateMachineDefinition definition,
        VariableResolver resolver,
        StepRunner runner,
        Map<String, List<Map<String, Object>>> stateSteps) {

    public Result execute() {
        String current = stateMachine.currentState();

        while (true) {
            var stateDef = definition.states().get(current);
            if (stateDef == null) {
                return Result.failed("Unknown state: " + current);
            }

            if (stateDef.isTerminal()) {
                if (!stateDef.steps().isEmpty()) {
                    var stepResult = executeSteps(stateDef);
                    if (!stepResult.isSuccess()) {
                        return stepResult;
                    }
                }
                return Result.of(Map.of("finalState", current));
            }

            if (!stateDef.steps().isEmpty()) {
                var stepResult = executeSteps(stateDef);
                if (!stepResult.isSuccess()) {
                    if (stateDef.onFailure() != null) {
                        String failTarget = stateDef.onFailure().intern();
                        stateMachine.transition(current.intern(), failTarget);
                        current = failTarget;
                        continue;
                    }
                    return stepResult;
                }
            }

            if (stateDef.next() != null) {
                String next = stateDef.next().intern();
                stateMachine.transition(current.intern(), next);
                current = next;
                continue;
            }

            if (!stateDef.events().isEmpty()) {
                return Result.of(Map.of("waitingForEvent", true, "currentState", current));
            }

            return Result.failed("State '" + current + "' has no next state and no events");
        }
    }

    private Result executeSteps(StateDefinition stateDef) {
        for (var stepMap : stateDef.steps()) {
            for (var entry : stepMap.entrySet()) {
                var stepName = entry.getKey();
                var params = entry.getValue() instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.<String, Object>of();

                var definition = io.casehub.yaml.plugin.api.Definition.of(stepName).execute((p, r) -> Result.of(Map.of())).build();
                var pluginStep = new io.casehub.yaml.step.catalog.ResolvedStep.PluginStep(
                        stepName, definition, params, Map.of());

                var result = runner.run(pluginStep, resolver);

                if (result.isSuccess()) {
                    scope.resultStore().recordSuccess(stepName, result.output());
                } else {
                    String message = result instanceof Result.Failure f ? f.message() : "unknown error";
                    scope.resultStore().recordFailure(stepName,
                            new io.casehub.yaml.core.orchestration.StepError(message, null, null));
                    return result;
                }
            }
        }
        return Result.of(Map.of());
    }
}
