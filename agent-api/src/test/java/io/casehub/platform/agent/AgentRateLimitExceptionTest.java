package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRateLimitExceptionTest {

    @Test
    void messageContainsRate() {
        var ex = new AgentRateLimitException(2.0);
        assertThat(ex.getMessage()).contains("2.0");
        assertThat(ex.getMessage()).contains("permits/sec");
    }

    @Test
    void retryAfterMillisComputedFromRate() {
        var ex = new AgentRateLimitException(2.0);
        assertThat(ex.retryAfterMillis()).isEqualTo(500L);
    }

    @Test
    void retryAfterMillisRoundsUp() {
        var ex = new AgentRateLimitException(3.0);
        assertThat(ex.retryAfterMillis()).isEqualTo(334L);
    }

    @Test
    void zeroRateDefaultsToOneSecond() {
        var ex = new AgentRateLimitException(0.0);
        assertThat(ex.retryAfterMillis()).isEqualTo(1000L);
    }

    @Test
    void permitsPerSecondExposed() {
        var ex = new AgentRateLimitException(5.0);
        assertThat(ex.permitsPerSecond()).isEqualTo(5.0);
    }
}
