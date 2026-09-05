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

    private DefaultRedistributionPolicy policy(double compress, double redistribute, double escalate) {
        return new DefaultRedistributionPolicy(compress, redistribute, escalate);
    }

    private RedistributionContext ctx(double pressure) {
        var cap = new ActorCapacity("a", pressure, Map.of("ctx", pressure), Instant.now());
        return new RedistributionContext("a", cap, "ctx", 0, Duration.ZERO);
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
    void at_redistribute_threshold() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.9)))
                .isInstanceOf(RedistributionDecision.Redistribute.class);
    }

    @Test
    void at_escalate_threshold() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.97)))
                .isInstanceOf(RedistributionDecision.Escalate.class);
    }

    @Test
    void custom_thresholds() {
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.55)))
                .isInstanceOf(RedistributionDecision.Compress.class);
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.65)))
                .isInstanceOf(RedistributionDecision.Redistribute.class);
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.75)))
                .isInstanceOf(RedistributionDecision.Escalate.class);
    }

    @Test
    void decision_includes_reason() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.9));
        assertThat(decision.reason())
                .contains("0.9")
                .contains("redistribute");
    }
}
