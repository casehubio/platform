package io.casehub.platform.agent;

import java.util.Objects;

public record AgentChatRequest(
        String userPrompt,
        String systemPrompt,
        String agentId,
        String model,
        Long timeoutSeconds
) {
    public AgentChatRequest {
        Objects.requireNonNull(userPrompt, "userPrompt");
    }
}
