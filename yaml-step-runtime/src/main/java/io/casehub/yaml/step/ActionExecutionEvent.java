package io.casehub.yaml.step;

import java.util.Map;

public record ActionExecutionEvent(
        String actionName,
        long durationMs,
        boolean success,
        Map<String, Object> metadata,
        String bindingType,
        String resultClassification,
        String actorId,
        String tenancyId,
        String inputHash,
        String parentStepName,
        String executionEnvironment) {

    public ActionExecutionEvent(String actionName, long durationMs,
                              boolean success, Map<String, Object> metadata) {
        this(actionName, durationMs, success, metadata,
             null, null, null, null, null, null, null);
    }
}
