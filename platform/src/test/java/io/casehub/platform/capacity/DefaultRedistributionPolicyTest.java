package io.casehub.platform.capacity;

import io.casehub.platform.api.capacity.CapacitySignal;
import io.casehub.platform.api.capacity.RedistributionAction;
import io.casehub.platform.api.capacity.RedistributionContext;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultRedistributionPolicyTest {

    private DefaultRedistributionPolicy policy(double compress, double redistribute, double escalate) {
        return new DefaultRedistributionPolicy(compress, redistribute, escalate);
    }

    private RedistributionContext ctx(double pressure) {
        var signal = new CapacitySignal("a", "aggregated", pressure, Instant.now());
        return new RedistributionContext("a", signal, List.of());
    }

    @Test
    void below_all_thresholds_returns_none() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.5)).action())
                .isEqualTo(RedistributionAction.NONE);
    }

    @Test
    void at_compress_threshold() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.75)).action())
                .isEqualTo(RedistributionAction.COMPRESS);
    }

    @Test
    void at_redistribute_threshold() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.9)).action())
                .isEqualTo(RedistributionAction.REDISTRIBUTE);
    }

    @Test
    void at_escalate_threshold() {
        assertThat(policy(0.7, 0.85, 0.95).evaluate(ctx(0.97)).action())
                .isEqualTo(RedistributionAction.ESCALATE);
    }

    @Test
    void custom_thresholds() {
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.55)).action())
                .isEqualTo(RedistributionAction.COMPRESS);
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.65)).action())
                .isEqualTo(RedistributionAction.REDISTRIBUTE);
        assertThat(policy(0.5, 0.6, 0.7).evaluate(ctx(0.75)).action())
                .isEqualTo(RedistributionAction.ESCALATE);
    }

    @Test
    void decision_includes_reason() {
        var decision = policy(0.7, 0.85, 0.95).evaluate(ctx(0.9));
        assertThat(decision.reason())
                .contains("0.9")
                .contains("redistribute");
    }
}
