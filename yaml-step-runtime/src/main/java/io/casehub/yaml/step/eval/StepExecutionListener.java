package io.casehub.yaml.step.eval;

import io.casehub.yaml.plugin.api.Result;
import io.casehub.yaml.step.catalog.ResolvedStep;

import java.util.Map;

public interface StepExecutionListener {

    void beforeStep(ResolvedStep step, int index);

    void afterStep(ResolvedStep step, int index, Result result, Map<String, Object> detail);
}
