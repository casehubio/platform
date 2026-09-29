package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.resolver.VariableResolver;
import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;

@FunctionalInterface
public interface StepRunner {

    Result run(ResolvedStep step, VariableResolver resolver);
}
