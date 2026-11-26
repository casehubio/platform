package io.casehub.platform.agent.guardrail;

import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaxTokenInputGuardrailTest {

    @Test
    void passesWhenUnderLimit() {
        var guardrail = new MaxTokenInputGuardrail();
        guardrail.maxTokens = 1000;
        var config = AgentSessionConfig.of("system prompt", "short user prompt");
        var result = guardrail.validate(config);
        assertThat(result).isInstanceOf(GuardrailResult.Pass.class);
    }

    @Test
    void failsWhenOverLimit() {
        var guardrail = new MaxTokenInputGuardrail();
        guardrail.maxTokens = 5;
        var config = AgentSessionConfig.of("a very long system prompt that exceeds the tiny limit", "and a user prompt too");
        var result = guardrail.validate(config);
        assertThat(result).isInstanceOf(GuardrailResult.Fail.class);
        assertThat(((GuardrailResult.Fail) result).reason()).contains("exceeds maximum token limit");
    }

    @Test
    void handlesNullSystemPromptGracefully() {
        var guardrail = new MaxTokenInputGuardrail();
        guardrail.maxTokens = 1000;
        var config = AgentSessionConfig.of("sys", "user");
        var result = guardrail.validate(config);
        assertThat(result).isInstanceOf(GuardrailResult.Pass.class);
    }
}
