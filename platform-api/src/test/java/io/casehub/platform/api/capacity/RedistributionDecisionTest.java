package io.casehub.platform.api.capacity;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class RedistributionDecisionTest {

    @Test
    void none_factory() {
        var decision = RedistributionDecision.none();
        assertThat(decision.action()).isEqualTo(RedistributionAction.NONE);
        assertThat(decision.reason()).isNull();
    }

    @Test
    void compress_factory() {
        var decision = RedistributionDecision.compress("high load");
        assertThat(decision.action()).isEqualTo(RedistributionAction.COMPRESS);
        assertThat(decision.reason()).isEqualTo("high load");
    }

    @Test
    void redistribute_factory() {
        var decision = RedistributionDecision.redistribute("overloaded");
        assertThat(decision.action()).isEqualTo(RedistributionAction.REDISTRIBUTE);
    }

    @Test
    void escalate_factory() {
        var decision = RedistributionDecision.escalate("critical");
        assertThat(decision.action()).isEqualTo(RedistributionAction.ESCALATE);
    }

    @Test
    void context_null_source_signals_defaults_empty() {
        var signal = new CapacitySignal("a", "aggregated", 0.5, Instant.now());
        var ctx = new RedistributionContext("a", signal, null);
        assertThat(ctx.sourceSignals()).isEmpty();
    }

    @Test
    void pressure_event_null_firedAt_defaults() {
        var signal = new CapacitySignal("a", "aggregated", 0.9, Instant.now());
        var decision = RedistributionDecision.escalate("critical");
        var before = Instant.now();
        var event = new CapacityPressureEvent("a", decision, signal, null);
        assertThat(event.firedAt()).isAfterOrEqualTo(before);
    }
}
