package io.casehub.yaml.step.error;

import io.casehub.yaml.core.condition.ConditionEvaluationException;
import io.casehub.yaml.core.error.*;
import io.casehub.yaml.core.module.ParameterValidationException;
import io.casehub.yaml.core.module.ParameterViolation;
import io.casehub.yaml.core.orchestration.*;
import io.casehub.yaml.core.resolver.UnresolvedVariableException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class YamlErrorMapperTest {

    private static final SourceLocation LOC = new SourceLocation("test.yaml", 10, -1);

    @Test
    void deadlineExceeded_mapsToDeadlineError() {
        var ex = new DeadlineExceededException("my-scope", Duration.ofSeconds(5));
        YamlError err = YamlErrorMapper.from(ex, "step-a", LOC);
        assertThat(err).isInstanceOf(CoordinationError.DeadlineError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.DEADLINE_EXCEEDED);
        var de = (CoordinationError.DeadlineError) err;
        assertThat(de.scopeName()).isEqualTo("my-scope");
        assertThat(de.deadline()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void channelClosed_mapsToChannelError() {
        var ex = new ChannelClosedException("order-ch");
        YamlError err = YamlErrorMapper.from(ex, "step-b", LOC);
        assertThat(err).isInstanceOf(CoordinationError.ChannelError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.CHANNEL_CLOSED);
    }

    @Test
    void semaphoreReentrancy_mapsToSemaphoreError() {
        var ex = new SemaphoreReentrancyException("gate", "step-c");
        YamlError err = YamlErrorMapper.from(ex, "step-c", LOC);
        assertThat(err).isInstanceOf(CoordinationError.SemaphoreError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.SEMAPHORE_REENTRANCY);
    }

    @Test
    void illegalTransition_mapsToTransitionError() {
        var ex = new IllegalTransitionException("fsm", "IDLE", "COMPLETED");
        YamlError err = YamlErrorMapper.from(ex, "step-d", LOC);
        assertThat(err).isInstanceOf(CoordinationError.TransitionError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.ILLEGAL_TRANSITION);
    }

    @Test
    void unresolvedVariable_mapsToExpressionEvalError() {
        var ex = new UnresolvedVariableException("orderId", "step-e", "No source found");
        YamlError err = YamlErrorMapper.from(ex, "step-e", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.ExpressionEvalError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.EXPRESSION_EVAL);
    }

    @Test
    void conditionEvaluation_mapsToExpressionEvalError() {
        var ex = new ConditionEvaluationException("Null pointer in expression");
        YamlError err = YamlErrorMapper.from(ex, "step-f", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.ExpressionEvalError.class);
    }

    @Test
    void parameterValidation_mapsToParameterError() {
        var ex = new ParameterValidationException(
                List.of(new ParameterViolation("retries", "min", "must be >= 1", 0, null)));
        YamlError err = YamlErrorMapper.from(ex, "step-g", LOC);
        assertThat(err).isInstanceOf(ParseError.ParameterError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.PARAMETER_VIOLATION);
    }

    @Test
    void unknownException_mapsToStepActionError() {
        var ex = new RuntimeException("Something unexpected");
        YamlError err = YamlErrorMapper.from(ex, "step-h", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.StepActionError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.STEP_ACTION_FAILED);
        assertThat(err.summary()).contains("Something unexpected");
    }

    @Test
    void nullStepName_acceptedGracefully() {
        var ex = new ChannelClosedException("ch");
        YamlError err = YamlErrorMapper.from(ex, null, LOC);
        assertThat(err.stepName()).isNull();
    }

    @Test
    void agentTimeout_mapsToTimeoutError() {
        var ex = new io.casehub.platform.agent.AgentTimeoutException(Duration.ofSeconds(30));
        YamlError err = YamlErrorMapper.from(ex, "step-i", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.TimeoutError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(((RuntimeStepError.TimeoutError) err).timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void agentRateLimit_mapsToRateLimitError() {
        var ex = new io.casehub.platform.agent.AgentRateLimitException(10.0);
        YamlError err = YamlErrorMapper.from(ex, "step-j", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.RateLimitError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.RATE_LIMIT);
        var rle = (RuntimeStepError.RateLimitError) err;
        assertThat(rle.permitsPerSecond()).isEqualTo(10.0);
    }

    @Test
    void agentSessionLimit_mapsToSessionLimitError() {
        var ex = new io.casehub.platform.agent.AgentSessionLimitException(5);
        YamlError err = YamlErrorMapper.from(ex, "step-k", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.SessionLimitError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.SESSION_LIMIT);
        assertThat(((RuntimeStepError.SessionLimitError) err).limit()).isEqualTo(5);
    }

    @Test
    void agentProcess_mapsToInfrastructureError() {
        var ex = new io.casehub.platform.agent.AgentProcessException("connection refused", null);
        YamlError err = YamlErrorMapper.from(ex, "step-l", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.InfrastructureError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.INFRASTRUCTURE);
    }

    @Test
    void accessDenied_mapsToAuthDeniedError() {
        var ex = new io.casehub.platform.api.acl.AccessDeniedException(
                "user-1",
                new io.casehub.platform.api.acl.ResourceId("CASE", "123"),
                io.casehub.platform.api.acl.AclAction.READ);
        YamlError err = YamlErrorMapper.from(ex, "step-m", LOC);
        assertThat(err).isInstanceOf(RuntimeStepError.AuthDeniedError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.AUTH_DENIED);
        var ade = (RuntimeStepError.AuthDeniedError) err;
        assertThat(ade.actorId()).isEqualTo("user-1");
        assertThat(ade.resource()).isEqualTo("CASE:123");
        assertThat(ade.action()).isEqualTo("read");
    }

    @Test
    void fromFailure_withKnownCategory_categorized() {
        var failure = new io.casehub.yaml.plugin.api.Result.Failure("timed out", "timeout", java.util.Map.of("timeout", "PT30S"));
        YamlError err = YamlErrorMapper.fromFailure(failure, "step-n");
        assertThat(err.category()).isEqualTo(ErrorCategory.TIMEOUT);
        assertThat(err.toDetailMap()).containsEntry("timeout", "PT30S");
    }

    @Test
    void fromFailure_withUnknownCategory_preservesOriginal() {
        var failure = new io.casehub.yaml.plugin.api.Result.Failure("transient", "transient");
        YamlError err = YamlErrorMapper.fromFailure(failure, "step-o");
        assertThat(err.category()).isEqualTo(ErrorCategory.STEP_ACTION_FAILED);
        assertThat(err.toDetailMap()).containsEntry("originalCategory", "transient");
    }

    @Test
    void fromFailure_withNullCategory_defaultsToStepActionError() {
        var failure = new io.casehub.yaml.plugin.api.Result.Failure("boom");
        YamlError err = YamlErrorMapper.fromFailure(failure, "step-p");
        assertThat(err).isInstanceOf(RuntimeStepError.StepActionError.class);
        assertThat(err.category()).isEqualTo(ErrorCategory.STEP_ACTION_FAILED);
    }
}
