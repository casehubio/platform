package io.casehub.yaml.step.handler;

import io.casehub.yaml.plugin.api.Result;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvokeHandlerCategoryTest {

    @Test
    void timeoutException_producesTimeoutCategory() {
        var result = simulateAgentFailure(
                new io.casehub.platform.agent.AgentTimeoutException(java.time.Duration.ofSeconds(30)));
        assertThat(result.isSuccess()).isFalse();
        var f = (Result.Failure) result;
        assertThat(f.category()).isEqualTo("timeout");
        assertThat(f.detail()).containsEntry("timeout", "PT30S");
    }

    @Test
    void rateLimitException_producesRateLimitCategory() {
        var result = simulateAgentFailure(
                new io.casehub.platform.agent.AgentRateLimitException(10.0));
        assertThat(result.isSuccess()).isFalse();
        var f = (Result.Failure) result;
        assertThat(f.category()).isEqualTo("rate-limit");
        assertThat(f.detail()).containsKey("permitsPerSecond");
    }

    @Test
    void sessionLimitException_producesSessionLimitCategory() {
        var result = simulateAgentFailure(
                new io.casehub.platform.agent.AgentSessionLimitException(5));
        assertThat(result.isSuccess()).isFalse();
        var f = (Result.Failure) result;
        assertThat(f.category()).isEqualTo("session-limit");
        assertThat(f.detail()).containsEntry("limit", 5);
    }

    @Test
    void processException_producesInfrastructureCategory() {
        var result = simulateAgentFailure(
                new io.casehub.platform.agent.AgentProcessException("connection refused", null));
        assertThat(result.isSuccess()).isFalse();
        var f = (Result.Failure) result;
        assertThat(f.category()).isEqualTo("infrastructure");
    }

    @Test
    void genericException_producesActionFailedCategory() {
        var result = simulateAgentFailure(new RuntimeException("unexpected"));
        assertThat(result.isSuccess()).isFalse();
        var f = (Result.Failure) result;
        assertThat(f.category()).isEqualTo("action-failed");
    }

    private Result simulateAgentFailure(Exception e) {
        try {
            throw e;
        } catch (io.casehub.platform.agent.AgentTimeoutException ex) {
            return Result.failed("Agent timed out: " + ex.getMessage(), "timeout",
                    Map.of("timeout", ex.timeout().toString()));
        } catch (io.casehub.platform.agent.AgentRateLimitException ex) {
            return Result.failed("Agent rate limited: " + ex.getMessage(), "rate-limit",
                    Map.of("permitsPerSecond", ex.permitsPerSecond(), "retryAfterMs", ex.retryAfterMillis()));
        } catch (io.casehub.platform.agent.AgentSessionLimitException ex) {
            return Result.failed("Agent session limit: " + ex.getMessage(), "session-limit",
                    Map.of("limit", ex.limit()));
        } catch (io.casehub.platform.agent.AgentProcessException ex) {
            return Result.failed("Agent process error: " + ex.getMessage(), "infrastructure");
        } catch (Exception ex) {
            return Result.failed("Agent invocation failed: " + ex.getMessage(), "action-failed");
        }
    }
}
