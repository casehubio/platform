package io.casehub.platform.agent.react;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record ToolUseResult(
        String text,
        long inputTokens,
        long outputTokens,
        int cycleCount,
        List<ToolCallRecord> toolCalls,
        Duration elapsed,
        TerminationReason reason) {
    public ToolUseResult {
        toolCalls = List.copyOf(toolCalls);
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(reason, "reason");
    }
}
