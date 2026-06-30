package io.casehub.platform.agent;

/**
 * Streaming event emitted by {@link AgentProvider#invoke} and {@link AgentSession#query}.
 *
 * <p>The event model captures the richest stream any reasonable LLM backend can produce.
 * Backends that don't support a particular event type (e.g. thinking, streaming tool
 * arguments) simply never emit it — consumers pattern-match only the variants they need.
 *
 * <p>Not all events are emitted by all backends:
 * <ul>
 *   <li>{@link TextDelta} — universal; every backend emits text.
 *   <li>{@link ThinkingDelta} — backends with extended thinking (Claude).
 *   <li>{@link ToolCallDelta} — backends that stream tool arguments token-by-token.
 *   <li>{@link ToolCallComplete} — universal for backends that support tool use.
 *   <li>{@link ToolResult} — observability; what the tool returned to the agent.
 *   <li>{@link InvocationComplete} — terminal; cost, usage, and timing metadata.
 * </ul>
 */
public sealed interface AgentEvent permits
        AgentEvent.TextDelta,
        AgentEvent.ThinkingDelta,
        AgentEvent.ToolCallDelta,
        AgentEvent.ToolCallComplete,
        AgentEvent.ToolResult,
        AgentEvent.InvocationComplete {

    record TextDelta(String text) implements AgentEvent {}

    record ThinkingDelta(String text) implements AgentEvent {
        public ThinkingDelta {
            if (text == null || text.isEmpty())
                throw new IllegalArgumentException("text must not be null or empty");
        }
    }

    record ToolCallDelta(int index, String id, String name, String partialArguments) implements AgentEvent {
        public ToolCallDelta {
            if (index < 0)
                throw new IllegalArgumentException("index must not be negative");
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("name must not be null or blank");
            if (partialArguments == null || partialArguments.isEmpty())
                throw new IllegalArgumentException("partialArguments must not be null or empty");
        }
    }

    record ToolCallComplete(int index, String id, String name, String arguments) implements AgentEvent {
        public ToolCallComplete {
            if (index < 0)
                throw new IllegalArgumentException("index must not be negative");
            if (name == null || name.isBlank())
                throw new IllegalArgumentException("name must not be null or blank");
            if (arguments == null || arguments.isEmpty())
                throw new IllegalArgumentException("arguments must not be null or empty");
        }
    }

    record ToolResult(String toolCallId, String content, boolean isError) implements AgentEvent {
        public ToolResult {
            if (content == null)
                throw new IllegalArgumentException("content must not be null");
        }
    }

    /**
     * Terminal event emitted once per invocation with cost, usage, and timing metadata.
     *
     * <p>Token counts are zero when the backend does not report them. {@code totalCostUsd}
     * is null when cost information is unavailable.
     */
    record InvocationComplete(
            int inputTokens, int outputTokens, int thinkingTokens,
            int cacheReadTokens, int cacheWriteTokens,
            Double totalCostUsd,
            long durationMs, long apiDurationMs,
            String sessionId, int numTurns,
            boolean isError) implements AgentEvent {}
}
