package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.StepResult;
import io.casehub.yaml.step.catalog.ResolvedStep;

@FunctionalInterface
public interface StepRunner {

    StepResult run(ResolvedStep step, VariableResolver resolver);
}
