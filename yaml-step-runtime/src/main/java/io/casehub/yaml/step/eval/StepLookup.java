package io.casehub.yaml.step.eval;

import io.casehub.yaml.step.catalog.ResolvedStep;

@FunctionalInterface
public interface StepLookup {
    ResolvedStep resolve(String stepName);
}
