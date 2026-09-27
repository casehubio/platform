package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.StepResult;

@FunctionalInterface
public interface DecoratedExecution {

    StepResult execute(VariableResolver resolver);
}
