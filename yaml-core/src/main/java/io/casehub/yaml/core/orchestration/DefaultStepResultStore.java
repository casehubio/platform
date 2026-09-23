package io.casehub.yaml.core.orchestration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultStepResultStore implements StepResultStore {

    private final ConcurrentHashMap<String, Map<String, Object>> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, StepError> errors = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> completed = new ConcurrentHashMap<>();

    @Override
    public void recordSuccess(String stepName, Map<String, Object> result) {
        results.put(stepName, result);
        completed.put(stepName, true);
    }

    @Override
    public void recordFailure(String stepName, StepError error) {
        errors.put(stepName, error);
        completed.put(stepName, true);
    }

    @Override
    public Map<String, Object> result(String stepName) {
        return results.get(stepName);
    }

    @Override
    public StepError error(String stepName) {
        return errors.get(stepName);
    }

    @Override
    public boolean hasCompleted(String stepName) {
        return completed.containsKey(stepName);
    }
}
