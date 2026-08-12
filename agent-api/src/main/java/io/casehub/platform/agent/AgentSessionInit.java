package io.casehub.platform.agent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record AgentSessionInit(
        String systemPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId,
        String model
) {
    public AgentSessionInit {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    /**
     * No MCP servers, default timeout, no correlation, no model.
     */
    public static AgentSessionInit of(String systemPrompt) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, null);
    }

    /**
     * Explicit model (provider key).
     */
    public static AgentSessionInit of(String systemPrompt, String model) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, model);
    }
}
