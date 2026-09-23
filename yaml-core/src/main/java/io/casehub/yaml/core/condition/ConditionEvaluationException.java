package io.casehub.yaml.core.condition;

public class ConditionEvaluationException extends RuntimeException {

    public ConditionEvaluationException(String message) {
        super(message);
    }

    public ConditionEvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}
