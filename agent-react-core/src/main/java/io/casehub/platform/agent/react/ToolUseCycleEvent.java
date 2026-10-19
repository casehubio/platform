package io.casehub.platform.agent.react;

import java.util.List;

public record ToolUseCycleEvent(
        int cycleIndex,
        String reasoningText,
        List<ToolCallRecord> toolCalls,
        long inputTokens,
        long outputTokens) {
    public ToolUseCycleEvent {
        toolCalls = List.copyOf(toolCalls);
    }
}
