package io.casehub.yaml.core.error;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class YamlErrorDetailMapTest {

    private static final SourceLocation LOC = new SourceLocation("test.yaml", 1, -1);

    @Test
    void stepActionError_detailMap() {
        var err = new RuntimeStepError.StepActionError("s", "rest-invoke", "boom", LOC, null);
        assertThat(err.toDetailMap()).containsEntry("actionName", "rest-invoke");
    }

    @Test
    void stepActionError_nullAction_omitted() {
        var err = new RuntimeStepError.StepActionError("s", null, "boom", LOC, null);
        assertThat(err.toDetailMap()).doesNotContainKey("actionName");
    }

    @Test
    void timeoutError_detailMap() {
        var err = new RuntimeStepError.TimeoutError("s", Duration.ofSeconds(30), LOC, null);
        assertThat(err.toDetailMap()).containsEntry("timeout", "PT30S");
    }

    @Test
    void retryExhaustedError_detailMap() {
        var err = new RuntimeStepError.RetryExhaustedError("s", 3, "last", "EXPONENTIAL", Duration.ofMillis(500), LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("maxAttempts", 3)
                .containsEntry("lastError", "last")
                .containsEntry("backoffStrategy", "EXPONENTIAL");
    }

    @Test
    void guardRejectedError_detailMap() {
        var err = new RuntimeStepError.GuardRejectedError("s", "x > 0", "negative", LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("guardExpression", "x > 0")
                .containsEntry("reason", "negative");
    }

    @Test
    void expressionEvalError_detailMap() {
        var err = new RuntimeStepError.ExpressionEvalError("s", "${foo}", "jq", "parse error", Map.of(), LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("expressionText", "${foo}")
                .containsEntry("engineType", "jq");
    }

    @Test
    void channelError_detailMap() {
        var err = new CoordinationError.ChannelError("s", "order-ch", LOC, null);
        assertThat(err.toDetailMap()).containsEntry("channelName", "order-ch");
    }

    @Test
    void deadlineError_detailMap() {
        var err = new CoordinationError.DeadlineError("s", "scope-a", Duration.ofSeconds(10), LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("scopeName", "scope-a")
                .containsEntry("deadline", "PT10S");
    }

    @Test
    void correlationError_detailMap() {
        var err = new CoordinationError.CorrelationError("s", "key-1", Duration.ofSeconds(5), LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("correlationKey", "key-1")
                .containsEntry("timeout", "PT5S");
    }

    @Test
    void semaphoreError_detailMap() {
        var err = new CoordinationError.SemaphoreError("s", "gate-1", LOC, null);
        assertThat(err.toDetailMap()).containsEntry("semaphoreName", "gate-1");
    }

    @Test
    void transitionError_detailMap() {
        var err = new CoordinationError.TransitionError("s", "fsm", "IDLE", "DONE", LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("machineName", "fsm")
                .containsEntry("fromState", "IDLE")
                .containsEntry("toState", "DONE");
    }

    @Test
    void unknownStepError_detailMap() {
        var err = new ParseError.UnknownStepError("s", "bad-action", LOC, null);
        assertThat(err.toDetailMap()).containsEntry("referencedAction", "bad-action");
    }

    @Test
    void parameterError_detailMap() {
        var err = new ParseError.ParameterError("s", "retries", "min >= 1", null, LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("parameterName", "retries")
                .containsEntry("constraint", "min >= 1");
    }

    @Test
    void expressionParseError_detailMap() {
        var err = new ParseError.ExpressionParseError("s", "${bad", "mvel", "unexpected EOF", LOC, null);
        assertThat(err.toDetailMap())
                .containsEntry("expressionText", "${bad")
                .containsEntry("engineType", "mvel")
                .containsEntry("engineMessage", "unexpected EOF");
    }

    @Test
    void structureError_detailMap() {
        var err = new ParseError.StructureError("s", "missing key", LOC, null);
        assertThat(err.toDetailMap()).containsEntry("detail", "missing key");
    }

    @Test
    void duplicateStepError_emptyDetailMap() {
        var err = new ParseError.DuplicateStepError("s", LOC, null);
        assertThat(err.toDetailMap()).isEmpty();
    }
}
