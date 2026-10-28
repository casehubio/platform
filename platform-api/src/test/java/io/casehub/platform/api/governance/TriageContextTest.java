package io.casehub.platform.api.governance;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TriageContextTest {

    @Test
    void validContext() {
        var ctx = new TriageContext("op-1", "playbook", 1, 2,
                "step 3 failed", Map.of("step", "3"), Map.of("case", "c-1"));

        assertThat(ctx.operationId()).isEqualTo("op-1");
        assertThat(ctx.operationType()).isEqualTo("playbook");
        assertThat(ctx.attemptNumber()).isEqualTo(1);
        assertThat(ctx.maxAttempts()).isEqualTo(2);
        assertThat(ctx.failureSummary()).isEqualTo("step 3 failed");
        assertThat(ctx.failureDetail()).containsEntry("step", "3");
        assertThat(ctx.executionContext()).containsEntry("case", "c-1");
    }

    @Test
    void nullOperationIdThrows() {
        assertThatThrownBy(() -> new TriageContext(null, "playbook", 1, 2,
                "fail", Map.of(), Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("operationId");
    }

    @Test
    void zeroAttemptNumberThrows() {
        assertThatThrownBy(() -> new TriageContext("op-1", "playbook", 0, 2,
                "fail", Map.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("attemptNumber");
    }

    @Test
    void nullMapsDefaultToEmpty() {
        var ctx = new TriageContext("op-1", "playbook", 1, 2, "fail", null, null);

        assertThat(ctx.failureDetail()).isEmpty();
        assertThat(ctx.executionContext()).isEmpty();
    }

    @Test
    void mapsAreDefensivelyCopied() {
        var detail = new HashMap<String, Object>();
        detail.put("key", "val");
        var ctx = new TriageContext("op-1", "playbook", 1, 2, "fail", detail, Map.of());

        assertThatThrownBy(() -> ctx.failureDetail().put("new", "val"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void triageDecisionRetry() {
        TriageDecision decision = new TriageDecision.Retry(Map.of("skip", "step-3"));

        assertThat(decision).isInstanceOf(TriageDecision.Retry.class);
        assertThat(((TriageDecision.Retry) decision).modifications())
            .containsEntry("skip", "step-3");
    }

    @Test
    void triageDecisionRetryNullModificationsDefaultsToEmpty() {
        TriageDecision decision = new TriageDecision.Retry(null);

        assertThat(((TriageDecision.Retry) decision).modifications()).isEmpty();
    }

    @Test
    void triageDecisionAbandon() {
        TriageDecision decision = new TriageDecision.Abandon("max attempts reached");

        assertThat(decision).isInstanceOf(TriageDecision.Abandon.class);
        assertThat(((TriageDecision.Abandon) decision).reason())
            .isEqualTo("max attempts reached");
    }

    @Test
    void abandonBlankReasonThrows() {
        assertThatThrownBy(() -> new TriageDecision.Abandon(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void abandonNullReasonThrows() {
        assertThatThrownBy(() -> new TriageDecision.Abandon(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coordinationExhaustedEvent() {
        var event = new CoordinationExhaustedEvent("op-1", "playbook", 2,
                "triage exhausted", Map.of("lastStep", "3"));

        assertThat(event.operationId()).isEqualTo("op-1");
        assertThat(event.triageAttempts()).isEqualTo(2);
        assertThat(event.lastFailureDetail()).containsEntry("lastStep", "3");
    }

    @Test
    void coordinationExhaustedEventNullDetailDefaultsToEmpty() {
        var event = new CoordinationExhaustedEvent("op-1", "playbook", 0,
                "exhausted", null);

        assertThat(event.lastFailureDetail()).isEmpty();
    }
}
