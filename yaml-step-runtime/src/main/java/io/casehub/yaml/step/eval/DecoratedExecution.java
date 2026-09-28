package io.casehub.yaml.step.eval;

import io.casehub.yaml.plugin.api.StepResult;

@FunctionalInterface
public interface DecoratedExecution {

    StepResult execute(StepContext ctx);
}
