package io.casehub.yaml.step.eval;

import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;

@FunctionalInterface
public interface StepEvaluator {
    Result evaluate(ResolvedStep step, StepContext ctx, StepRunner runner);
}
