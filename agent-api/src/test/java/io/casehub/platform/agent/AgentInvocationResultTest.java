package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentInvocationResultTest {

    @Test
    void recordAccessorsWork() {
        var result = new AgentInvocationResult(
                "hello world", 100, 50, 10, 20, 5, 0.003, 1500L, "openai", "default");
        assertThat(result.text()).isEqualTo("hello world");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(50);
        assertThat(result.thinkingTokens()).isEqualTo(10);
        assertThat(result.cacheReadTokens()).isEqualTo(20);
        assertThat(result.cacheWriteTokens()).isEqualTo(5);
        assertThat(result.totalCostUsd()).isEqualTo(0.003);
        assertThat(result.durationMs()).isEqualTo(1500L);
        assertThat(result.backendKey()).isEqualTo("openai");
        assertThat(result.instanceId()).isEqualTo("default");
    }

    @Test
    void nullCostIsAllowed() {
        var result = new AgentInvocationResult("text", 0, 0, 0, 0, 0, null, 0L, "claude", "default");
        assertThat(result.totalCostUsd()).isNull();
    }

    @Test
    void fromInvocationCompleteExtractsFields() {
        var complete = new AgentEvent.InvocationComplete(
                100, 50, 10, 20, 5, 0.003, 1500L, 1400L,
                "session-1", 3, false, "openai", "default");
        var result = AgentInvocationResult.from("hello", complete);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(50);
        assertThat(result.backendKey()).isEqualTo("openai");
    }

    @Test
    void fromNullCompletionUsesDefaults() {
        var result = AgentInvocationResult.from("hello", null);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.inputTokens()).isZero();
        assertThat(result.totalCostUsd()).isNull();
        assertThat(result.backendKey()).isNull();
    }
}
