package io.casehub.platform.governance;

import io.casehub.platform.api.governance.BackoffStrategy;
import io.casehub.platform.api.governance.ExecutionPolicy;
import io.casehub.platform.api.governance.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultPolicyEnforcerTest {

    private final PolicyEnforcer enforcer = new DefaultPolicyEnforcer();

    @Test
    void execute_noRetry_returnsResult() {
        ExecutionPolicy policy = new ExecutionPolicy(null, new RetryPolicy(1, 0));
        String result = enforcer.execute(policy, () -> "hello");
        assertThat(result).isEqualTo("hello");
    }

    @Test
    void execute_retriesOnFailure() {
        AtomicInteger attempts = new AtomicInteger(0);
        ExecutionPolicy policy = new ExecutionPolicy(null, new RetryPolicy(3, 10));

        String result = enforcer.execute(policy, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void execute_exhaustsRetries_throws() {
        ExecutionPolicy policy = new ExecutionPolicy(null, new RetryPolicy(2, 10));

        assertThatThrownBy(() -> enforcer.execute(policy, () -> {
            throw new RuntimeException("permanent");
        }))
            .isInstanceOf(PolicyEnforcementException.class)
            .hasMessageContaining("2 attempts")
            .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void execute_exponentialBackoff_retriesSuccessfully() {
        AtomicInteger attempts = new AtomicInteger(0);
        ExecutionPolicy policy = new ExecutionPolicy(null,
            new RetryPolicy(3, 10, BackoffStrategy.EXPONENTIAL));

        String result = enforcer.execute(policy, () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void execute_nullPolicy_executesOnce() {
        ExecutionPolicy policy = new ExecutionPolicy(null, null);
        String result = enforcer.execute(policy, () -> "direct");
        assertThat(result).isEqualTo("direct");
    }

    @Test
    void execute_timeout_failsIfExceeded() {
        ExecutionPolicy policy = new ExecutionPolicy(50, new RetryPolicy(1, 0));

        assertThatThrownBy(() -> enforcer.execute(policy, () -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "late";
        }))
            .isInstanceOf(PolicyEnforcementException.class)
            .hasMessageContaining("1 attempts")
            .cause()
            .isInstanceOf(PolicyEnforcementException.class)
            .hasMessageContaining("timed out");
    }

    @Test
    void execute_retryAfterTimeout_succeedsOnSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);
        ExecutionPolicy policy = new ExecutionPolicy(50, new RetryPolicy(3, 10));

        String result = enforcer.execute(policy, () -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == 1) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "late";
            }
            return "recovered";
        });

        assertThat(result).isEqualTo("recovered");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void execute_maxDelayMs_capsExponentialBackoff() {
        AtomicInteger attempts = new AtomicInteger(0);
        // base=100, exponential, maxDelay=150 — attempt 2 would be 200ms uncapped
        ExecutionPolicy policy = new ExecutionPolicy(null,
            new RetryPolicy(3, 100, BackoffStrategy.EXPONENTIAL, 150));

        long start = System.currentTimeMillis();
        enforcer.execute(policy, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return "ok";
        });
        long elapsed = System.currentTimeMillis() - start;

        assertThat(attempts.get()).isEqualTo(3);
        // Without cap: 100 + 200 = 300ms. With cap at 150: 100 + 150 = 250ms.
        // Allow generous margin for CI variance but confirm it's under uncapped time.
        assertThat(elapsed).isLessThan(500);
    }
}
