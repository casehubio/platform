package io.casehub.platform.api.governance;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionPolicyTest {

    @Test
    void defaultPolicy_hasDefaultRetry() {
        ExecutionPolicy policy = new ExecutionPolicy();
        assertThat(policy.timeoutMs()).isNull();
        assertThat(policy.retries()).isNotNull();
        assertThat(policy.retries().maxAttempts()).isEqualTo(3);
        assertThat(policy.retries().delayMs()).isEqualTo(10000);
        assertThat(policy.retries().backoffStrategy()).isEqualTo(BackoffStrategy.FIXED);
        assertThat(policy.retries().maxDelayMs()).isNull();
    }

    @Test
    void retryPolicy_defaultBackoff() {
        RetryPolicy retry = new RetryPolicy(5, 2000);
        assertThat(retry.backoffStrategy()).isEqualTo(BackoffStrategy.FIXED);
        assertThat(retry.maxDelayMs()).isNull();
    }

    @Test
    void customPolicy() {
        RetryPolicy retry = new RetryPolicy(5, 500, BackoffStrategy.EXPONENTIAL_WITH_JITTER);
        ExecutionPolicy policy = new ExecutionPolicy(30000, retry);
        assertThat(policy.timeoutMs()).isEqualTo(30000);
        assertThat(policy.retries().maxAttempts()).isEqualTo(5);
        assertThat(policy.retries().backoffStrategy()).isEqualTo(BackoffStrategy.EXPONENTIAL_WITH_JITTER);
    }

    @Test
    void retryPolicy_withMaxDelay() {
        RetryPolicy retry = new RetryPolicy(5, 100, BackoffStrategy.EXPONENTIAL, 5000);
        assertThat(retry.maxDelayMs()).isEqualTo(5000);
    }

    @Test
    void retryPolicy_rejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> new RetryPolicy(0, 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts must be >= 1");

        assertThatThrownBy(() -> new RetryPolicy(-1, 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxAttempts must be >= 1");
    }

    @Test
    void retryPolicy_rejectsNegativeDelay() {
        assertThatThrownBy(() -> new RetryPolicy(1, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("delayMs must be >= 0");
    }

    @Test
    void retryPolicy_rejectsNegativeMaxDelay() {
        assertThatThrownBy(() -> new RetryPolicy(1, 100, BackoffStrategy.EXPONENTIAL, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxDelayMs must be >= 0");
    }

    @Test
    void retryPolicy_allowsNullFields() {
        RetryPolicy retry = new RetryPolicy(null, null, null, null);
        assertThat(retry.maxAttempts()).isNull();
        assertThat(retry.delayMs()).isNull();
        assertThat(retry.backoffStrategy()).isNull();
        assertThat(retry.maxDelayMs()).isNull();
    }

    @Test
    void noRetry_executesOnceWithNoTimeout() {
        ExecutionPolicy policy = ExecutionPolicy.noRetry();
        assertThat(policy.timeoutMs()).isNull();
        assertThat(policy.retries().maxAttempts()).isEqualTo(1);
        assertThat(policy.retries().delayMs()).isEqualTo(0);
    }
}
