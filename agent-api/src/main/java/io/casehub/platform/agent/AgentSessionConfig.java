package io.casehub.platform.agent;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record AgentSessionConfig(
        String systemPrompt,
        String userPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId,
        String model
) {
    public AgentSessionConfig {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    /**
     * Default timeout, no MCP servers, no correlation, no model.
     */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, null);
    }

    /**
     * Explicit timeout.
     */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        Duration timeout) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), timeout, null, null);
    }

    /**
     * Explicit model (provider key).
     */
    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        String model) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, model);
    }
}
