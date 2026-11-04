package io.casehub.yaml.core.error;

import java.time.Duration;
import java.util.Map;

public sealed interface RuntimeStepError extends YamlError {

    record StepActionError(String stepName, String actionName, String rootCause,
                           SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.STEP_ACTION_FAILED; }
        @Override public String summary() {
            return "Step '" + stepName + "' action '" + actionName + "' failed: " + rootCause;
        }
        @Override public Map<String, Object> toDetailMap() {
            return actionName != null ? Map.of("actionName", actionName) : Map.of();
        }
    }

    record TimeoutError(String stepName, Duration timeout,
                        SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.TIMEOUT; }
        @Override public String summary() {
            return "Step '" + stepName + "' timed out after " + timeout;
        }
        @Override public Map<String, Object> toDetailMap() {
            return Map.of("timeout", timeout.toString());
        }
    }

    record RetryExhaustedError(String stepName, int maxAttempts, String lastError,
                               String backoffStrategy, Duration delay,
                               SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.RETRY_EXHAUSTED; }
        @Override public String summary() {
            return "Step '" + stepName + "' failed after " + maxAttempts
                    + " retry attempts. Last error: " + lastError;
        }
        @Override public Map<String, Object> toDetailMap() {
            return Map.of("maxAttempts", maxAttempts, "lastError", lastError, "backoffStrategy", backoffStrategy);
        }
    }

    record GuardRejectedError(String stepName, String guardExpression, String reason,
                              SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.GUARD_REJECTED; }
        @Override public String summary() {
            return "Step '" + stepName + "' guard rejected: " + reason;
        }
        @Override public Map<String, Object> toDetailMap() {
            return Map.of("guardExpression", guardExpression, "reason", reason);
        }
    }

    record ExpressionEvalError(String stepName, String expressionText, String engineType,
                               String engineMessage, Map<String, Object> resolvedVariables,
                               SourceLocation location, Throwable cause) implements RuntimeStepError {
        public ExpressionEvalError {
            resolvedVariables = resolvedVariables != null ? Map.copyOf(resolvedVariables) : Map.of();
        }
        @Override public ErrorCategory category() { return ErrorCategory.EXPRESSION_EVAL; }
        @Override public String summary() {
            return engineType + " expression failed on step '" + stepName
                    + "': " + engineMessage;
        }
        @Override public Map<String, Object> toDetailMap() {
            return Map.of("expressionText", expressionText != null ? expressionText : "", "engineType", engineType);
        }
    }

    record RateLimitError(String stepName, double permitsPerSecond, long retryAfterMs,
                          SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.RATE_LIMIT; }
        @Override public String summary() {
            return "Step '" + stepName + "' rate limited (" + permitsPerSecond + " permits/sec)";
        }
        @Override public Map<String, Object> toDetailMap() {
            return Map.of("permitsPerSecond", permitsPerSecond, "retryAfterMs", retryAfterMs);
        }
    }

    record SessionLimitError(String stepName, int limit,
                             SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.SESSION_LIMIT; }
        @Override public String summary() {
            return "Step '" + stepName + "' session limit reached (" + limit + " active)";
        }
        @Override public Map<String, Object> toDetailMap() {
            return Map.of("limit", limit);
        }
    }

    record InfrastructureError(String stepName, String component, String rootCause,
                               SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.INFRASTRUCTURE; }
        @Override public String summary() {
            return "Step '" + stepName + "' infrastructure error in '" + component + "': " + rootCause;
        }
        @Override public Map<String, Object> toDetailMap() {
            var map = new java.util.LinkedHashMap<String, Object>();
            map.put("component", component);
            if (rootCause != null) map.put("rootCause", rootCause);
            return Map.copyOf(map);
        }
    }

    record AuthDeniedError(String stepName, String actorId, String resource, String action,
                           SourceLocation location, Throwable cause) implements RuntimeStepError {
        @Override public ErrorCategory category() { return ErrorCategory.AUTH_DENIED; }
        @Override public String summary() {
            return "Step '" + stepName + "' access denied: actor=" + actorId
                    + " resource=" + resource + " action=" + action;
        }
        @Override public Map<String, Object> toDetailMap() {
            return Map.of("actorId", actorId, "resource", resource, "action", action);
        }
    }

    record CategorizedStepActionError(String stepName, String actionName, String rootCause,
                                       ErrorCategory overrideCategory, String originalCategory,
                                       Map<String, Object> failureDetail,
                                       SourceLocation location, Throwable cause) implements RuntimeStepError {
        public CategorizedStepActionError {
            failureDetail = failureDetail != null ? Map.copyOf(failureDetail) : Map.of();
        }
        @Override public ErrorCategory category() { return overrideCategory; }
        @Override public String summary() {
            return "Step '" + stepName + "' failed: " + rootCause;
        }
        @Override public Map<String, Object> toDetailMap() {
            var map = new java.util.LinkedHashMap<String, Object>(failureDetail);
            if (actionName != null) map.put("actionName", actionName);
            if (originalCategory != null) map.put("originalCategory", originalCategory);
            return Map.copyOf(map);
        }
    }
}
