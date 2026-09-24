package io.casehub.yaml.plugin.api;

import java.util.Map;

public sealed interface StepResult permits StepResult.Success, StepResult.Failure {

    boolean isSuccess();

    Map<String, Object> output();

    record Success(Map<String, Object> output) implements StepResult {
        public Success { output = Map.copyOf(output); }
        @Override public boolean isSuccess() { return true; }
    }

    record Failure(String message) implements StepResult {
        @Override public boolean isSuccess() { return false; }
        @Override public Map<String, Object> output() { return Map.of(); }
    }

    static StepResult of(Map<String, Object> output) { return new Success(output); }

    static StepResult failed(String message) { return new Failure(message); }
}
