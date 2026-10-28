package io.casehub.platform.agent;

import java.util.Map;

public record AgentChatResponse(
        String text,
        String agentId,
        Map<String, String> agentMetadata,
        String backendKey,
        String instanceId,
        int inputTokens,
        int outputTokens,
        int thinkingTokens,
        int cacheReadTokens,
        int cacheWriteTokens,
        Double totalCostUsd,
        long durationMs
) {
    public AgentChatResponse {
        agentMetadata = agentMetadata != null ? Map.copyOf(agentMetadata) : Map.of();
    }
}
