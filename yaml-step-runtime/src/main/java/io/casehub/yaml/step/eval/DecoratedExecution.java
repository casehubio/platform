package io.casehub.yaml.step.eval;

import io.casehub.yaml.plugin.api.Result;

@FunctionalInterface
public interface DecoratedExecution {

    Result execute(StepContext ctx);
}
