package io.casehub.platform.api.capacity;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class RedistributionDecisionTest {

    @Test
    void compress_factory() {
        var decision = RedistributionDecision.compress("high load");
        assertThat(decision).isInstanceOf(RedistributionDecision.Compress.class);
        assertThat(decision.reason()).isEqualTo("high load");
    }

    @Test
    void redistribute_factory() {
        var decision = RedistributionDecision.redistribute("overloaded");
        assertThat(decision).isInstanceOf(RedistributionDecision.Redistribute.class);
        assertThat(decision.reason()).isEqualTo("overloaded");
    }

    @Test
    void hold_factory() {
        var decision = RedistributionDecision.hold("stable");
        assertThat(decision).isInstanceOf(RedistributionDecision.Hold.class);
        assertThat(decision.reason()).isEqualTo("stable");
    }

    @Test
    void escalate_factory() {
        var decision = RedistributionDecision.escalate("critical");
        assertThat(decision).isInstanceOf(RedistributionDecision.Escalate.class);
        assertThat(decision.reason()).isEqualTo("critical");
    }

    @Test
    void redistribute_with_grace_period() {
        var decision = new RedistributionDecision.Redistribute(
                "overloaded", Duration.ofMinutes(5), java.util.Set.of("agent-3"));
        assertThat(decision.gracePeriod()).isEqualTo(Duration.ofMinutes(5));
        assertThat(decision.excludeActors()).containsExactly("agent-3");
    }

    @Test
    void context_null_trigger_defaults_unknown() {
        var cap = new ActorCapacity("a", 0.5, Map.of(), Instant.now());
        var ctx = new RedistributionContext("a", cap, null, 0, null);
        assertThat(ctx.triggerSignalType()).isEqualTo("unknown");
    }

    @Test
    void context_null_duration_defaults_zero() {
        var cap = new ActorCapacity("a", 0.5, Map.of(), Instant.now());
        var ctx = new RedistributionContext("a", cap, "ctx", 0, null);
        assertThat(ctx.timeSinceLastActivity()).isEqualTo(Duration.ZERO);
    }
}
