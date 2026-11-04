package io.casehub.yaml.step.error;

import io.casehub.yaml.core.condition.ConditionEvaluationException;
import io.casehub.yaml.core.error.*;
import io.casehub.yaml.core.module.ParameterValidationException;
import io.casehub.yaml.core.orchestration.*;
import io.casehub.yaml.core.resolver.UnresolvedVariableException;
import io.casehub.yaml.plugin.api.Result;

import java.util.Map;

public final class YamlErrorMapper {

    private YamlErrorMapper() {}

    public static YamlError from(Throwable t, String stepName, SourceLocation location) {
        return switch (t) {
            case DeadlineExceededException e ->
                    new CoordinationError.DeadlineError(stepName, e.scopeName(), e.deadline(), location, e);
            case ChannelClosedException e ->
                    new CoordinationError.ChannelError(stepName, e.getMessage(), location, e);
            case CorrelationTimeoutException e ->
                    new CoordinationError.CorrelationError(stepName, null, null, location, e);
            case SemaphoreReentrancyException e ->
                    new CoordinationError.SemaphoreError(stepName, e.getMessage(), location, e);
            case IllegalTransitionException e ->
                    new CoordinationError.TransitionError(stepName, null, null, null, location, e);
            case UnresolvedVariableException e ->
                    new RuntimeStepError.ExpressionEvalError(
                            stepName, e.variableName(), "variable",
                            e.getMessage(), Map.of(), location, e);
            case ConditionEvaluationException e ->
                    new RuntimeStepError.ExpressionEvalError(
                            stepName, null, "condition",
                            e.getMessage(), Map.of(), location, e);
            case ParameterValidationException e ->
                    new ParseError.ParameterError(
                            stepName,
                            e.violations().isEmpty() ? "" : e.violations().get(0).parameterName(),
                            e.getMessage(), null, location, e);
            case io.casehub.platform.agent.AgentTimeoutException e ->
                    new RuntimeStepError.TimeoutError(stepName, e.timeout(), location, e);
            case io.casehub.platform.agent.AgentRateLimitException e ->
                    new RuntimeStepError.RateLimitError(stepName, e.permitsPerSecond(), e.retryAfterMillis(), location, e);
            case io.casehub.platform.agent.AgentSessionLimitException e ->
                    new RuntimeStepError.SessionLimitError(stepName, e.limit(), location, e);
            case io.casehub.platform.agent.AgentProcessException e ->
                    new RuntimeStepError.InfrastructureError(stepName, "agent-process",
                            e.getMessage() != null ? e.getMessage() : "unknown", location, e);
            case io.casehub.platform.api.acl.AccessDeniedException e ->
                    new RuntimeStepError.AuthDeniedError(stepName, e.actorId(),
                            e.resourceId().toString(), e.action().name().toLowerCase(), location, e);
            default ->
                    new RuntimeStepError.StepActionError(
                            stepName, null, t.getMessage(), location, t);
        };
    }

    public static YamlError fromFailure(Result.Failure failure, String stepName) {
        if (failure.category() != null) {
            ErrorCategory cat = ErrorCategory.fromYamlKey(failure.category());
            if (cat != null) {
                return new RuntimeStepError.CategorizedStepActionError(
                        stepName, null, failure.message(), cat, null,
                        failure.detail(), null, null);
            }
            return new RuntimeStepError.CategorizedStepActionError(
                    stepName, null, failure.message(), ErrorCategory.STEP_ACTION_FAILED,
                    failure.category(), failure.detail(), null, null);
        }
        return new RuntimeStepError.StepActionError(
                stepName, null, failure.message(), null, null);
    }
}
