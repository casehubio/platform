package io.casehub.platform.agent;

public sealed interface AgentEvent permits AgentEvent.TextDelta {

    /**
     * A token-level streaming chunk. NOT a buffered complete response.
     * Accumulate deltas to build the full response.
     *
     * <p>Absent intentionally:
     * <ul>
     *   <li>ToolCall: Claude Code tool invocations are opaque to the observer.
     *   <li>UsageReport: SDK cost metadata out of scope for v1.
     * </ul>
     */
    record TextDelta(String text) implements AgentEvent {}
}
