package io.casehub.platform.api.governance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircuitBreakerPolicyTest {

    @Test
    void defaults() {
        var cb = new CircuitBreakerPolicy();
        assertThat(cb.failureThreshold()).isEqualTo(5);
        assertThat(cb.recoveryWindowMs()).isEqualTo(30000);
    }

    @Test
    void customValues() {
        var cb = new CircuitBreakerPolicy(3, 10000);
        assertThat(cb.failureThreshold()).isEqualTo(3);
        assertThat(cb.recoveryWindowMs()).isEqualTo(10000);
    }

    @Test
    void rejectsInvalidThreshold() {
        assertThatThrownBy(() -> new CircuitBreakerPolicy(0, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeRecoveryWindow() {
        assertThatThrownBy(() -> new CircuitBreakerPolicy(5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executionPolicy_withCircuitBreaker() {
        var policy = new ExecutionPolicy(5000,
                new RetryPolicy(3, 1000),
                new CircuitBreakerPolicy(5, 30000));
        assertThat(policy.circuitBreaker()).isNotNull();
        assertThat(policy.circuitBreaker().failureThreshold()).isEqualTo(5);
    }

    @Test
    void executionPolicy_backwardCompat_noCircuitBreaker() {
        var policy = new ExecutionPolicy(5000, new RetryPolicy());
        assertThat(policy.circuitBreaker()).isNull();
    }

    @Test
    void executionPolicy_defaultConstructor_noCircuitBreaker() {
        var policy = new ExecutionPolicy();
        assertThat(policy.circuitBreaker()).isNull();
    }

    @Test
    void executionPolicy_noRetry_noCircuitBreaker() {
        var policy = ExecutionPolicy.noRetry();
        assertThat(policy.circuitBreaker()).isNull();
    }
}
