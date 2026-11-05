package io.casehub.yaml.step.eval;

import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;

import java.util.Map;

@FunctionalInterface
public interface StepExecutionListener {

    void onStepComplete(ResolvedStep step, int index, Result result,
                        long durationMs, Map<String, Object> detail);
}
