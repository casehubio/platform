package io.casehub.yaml.core.orchestration;

import java.util.Map;

public interface StepResultStore {
    void recordSuccess(String stepName, Map<String, Object> result);
    void recordFailure(String stepName, StepError error);
    Map<String, Object> result(String stepName);
    StepError error(String stepName);
    boolean hasCompleted(String stepName);
}
