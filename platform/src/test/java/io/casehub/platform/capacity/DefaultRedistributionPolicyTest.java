package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.ActorCapacity;
import io.casehub.platform.api.capacity.RedistributionContext;
import io.casehub.platform.api.capacity.RedistributionDecision;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultRedistributionPolicyTest {

    private DefaultRedistributionPolicy policy(double compress, double redistribute, double immediate) {
        return new DefaultRedistributionPolicy(compress, redistribute, immediate,
                Duration.ofSeconds(30), Duration.ofMinutes(5));
    }

    private RedistributionContext ctx(double pressure) {
        return ctx(pressure, 0, Duration.ofSeconds(30));
    }

    private RedistributionContext ctx(double pressure, int obligations, Duration inactivity) {
        var cap = new ActorCapacity("agent-1", pressure, Map.of("ctx", pressure), Instant.now());
        return new RedistributionContext("agent-1", cap, "ctx", obligations, inactivity);
    }

    @Test
    void below_all_thresholds_returns_hold() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.5)))
                .isInstanceOf(RedistributionDecision.Hold.class);
    }

    @Test
    void at_compress_threshold() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.75)))
                .isInstanceOf(RedistributionDecision.Compress.class);
    }

    @Test
    void at_redistribute_threshold_with_obligations() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.9, 3, Duration.ofSeconds(30))))
                .isInstanceOf(RedistributionDecision.Redistribute.class);
    }

    @Test
    void at_redistribute_threshold_zero_obligations_returns_hold() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.9, 0, Duration.ofSeconds(30)));
        assertThat(decision).isInstanceOf(RedistributionDecision.Hold.class);
        assertThat(decision.reason()).contains("no movable obligations");
    }

    @Test
    void immediate_redistribute_above_immediate_threshold() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.96, 3, Duration.ofSeconds(30)));
        assertThat(decision).isInstanceOf(RedistributionDecision.Redistribute.class);
        var redistribute = (RedistributionDecision.Redistribute) decision;
        assertThat(redistribute.gracePeriod()).isEqualTo(Duration.ZERO);
        assertThat(redistribute.excludeActors()).contains("agent-1");
    }

    @Test
    void redistribute_with_grace_period_between_thresholds() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.88, 2, Duration.ofSeconds(30)));
        assertThat(decision).isInstanceOf(RedistributionDecision.Redistribute.class);
        var redistribute = (RedistributionDecision.Redistribute) decision;
        assertThat(redistribute.gracePeriod()).isEqualTo(Duration.ofSeconds(30));
        assertThat(redistribute.excludeActors()).contains("agent-1");
    }

    @Test
    void inactivity_escalation() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.5, 2, Duration.ofMinutes(6)));
        assertThat(decision).isInstanceOf(RedistributionDecision.Escalate.class);
        assertThat(decision.reason()).contains("inactive");
    }

    @Test
    void inactivity_takes_precedence_over_pressure() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.96, 3, Duration.ofMinutes(6)));
        assertThat(decision).isInstanceOf(RedistributionDecision.Escalate.class);
    }

    @Test
    void custom_thresholds() {
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.55)))
                .isInstanceOf(RedistributionDecision.Compress.class);
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.65, 2, Duration.ofSeconds(10))))
                .isInstanceOf(RedistributionDecision.Redistribute.class);
    }

    @Test
    void decision_includes_reason() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.9, 1, Duration.ofSeconds(10)));
        assertThat(decision.reason())
                .contains("0.9")
                .contains("redistribute");
    }
}
