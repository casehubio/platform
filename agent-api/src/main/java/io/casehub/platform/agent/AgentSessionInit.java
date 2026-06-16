package io.casehub.platform.agent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for opening a multi-turn {@link AgentSession}.
 *
 * <p>Distinct from {@link AgentSessionConfig}: multi-turn sessions have no per-open
 * user prompt — prompts are sent via {@link AgentSession#query(String)}.
 */
public record AgentSessionInit(
        String systemPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId
) {
    public AgentSessionInit {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    /** No MCP servers, default timeout, no correlation. */
    public static AgentSessionInit of(final String systemPrompt) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null);
    }
}
