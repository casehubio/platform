package io.casehub.platform.agent;

public record AgentInvocationResult(
        String text,
        int inputTokens,
        int outputTokens,
        int thinkingTokens,
        int cacheReadTokens,
        int cacheWriteTokens,
        Double totalCostUsd,
        long durationMs,
        String backendKey,
        String instanceId
) {
    public static AgentInvocationResult from(String text, AgentEvent.InvocationComplete completion) {
        if (completion == null) {
            return new AgentInvocationResult(text, 0, 0, 0, 0, 0, null, 0L, null, null);
        }
        return new AgentInvocationResult(
                text,
                completion.inputTokens(),
                completion.outputTokens(),
                completion.thinkingTokens(),
                completion.cacheReadTokens(),
                completion.cacheWriteTokens(),
                completion.totalCostUsd(),
                completion.durationMs(),
                completion.backendKey(),
                completion.instanceId());
    }
}
