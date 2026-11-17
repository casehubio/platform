package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.condition.ConditionEvaluator;
import io.casehub.yaml.core.orchestration.ExecutionScope;
import io.casehub.yaml.core.runtime.SpeedMultiplier;

public record DecoratorConfig(
        ConditionEvaluator conditionEvaluator,
        SpeedMultiplier speedMultiplier,
        ExecutionScope scope,
        StepLookup stepLookup,
        StepEvaluator handlerEvaluator,
        StepRunner handlerRunner,
        TransformDelegate transformDelegate
) {}
