package io.casehub.platform.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AgentChatRequestTest {

    @Test
    void userPromptRequired() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AgentChatRequest(null, "system", null, null, null));
    }

    @Test
    void explicitMode() {
        var request = new AgentChatRequest("What is 2+2?", "You are a calculator", null, null, null);
        assertThat(request.userPrompt()).isEqualTo("What is 2+2?");
        assertThat(request.systemPrompt()).isEqualTo("You are a calculator");
        assertThat(request.agentId()).isNull();
    }

    @Test
    void resolvedMode() {
        var request = new AgentChatRequest("Hello", null, "my-agent", null, null);
        assertThat(request.agentId()).isEqualTo("my-agent");
        assertThat(request.systemPrompt()).isNull();
    }

    @Test
    void optionalOverrides() {
        var request = new AgentChatRequest("Hello", null, "my-agent", "claude-opus-4-6", 60L);
        assertThat(request.model()).isEqualTo("claude-opus-4-6");
        assertThat(request.timeoutSeconds()).isEqualTo(60L);
    }
}
