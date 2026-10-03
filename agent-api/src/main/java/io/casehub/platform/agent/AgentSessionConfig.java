package io.casehub.platform.agent;

import io.casehub.platform.api.model.ModelChain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record AgentSessionConfig(
        String systemPrompt,
        String userPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId,
        String model,
        io.casehub.platform.api.model.ModelQuery modelQuery,
        ModelChain modelChain
) {
    public AgentSessionConfig {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt,
                              List<AgentMcpServer> mcpServers, Duration timeout,
                              String correlationId, String model,
                              io.casehub.platform.api.model.ModelQuery modelQuery) {
        this(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, null);
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt,
                              List<AgentMcpServer> mcpServers, Duration timeout,
                              String correlationId, String model) {
        this(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, null, null);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, null, null, null);
    }

    public AgentSessionConfig withModel(String model) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, null, null);
    }

    public AgentSessionConfig withModel(io.casehub.platform.api.model.ModelQuery modelQuery) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, null, modelQuery, null);
    }

    public AgentSessionConfig withModelChain(ModelChain chain) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, null, null, chain);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        Duration timeout) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), timeout, null, null, null, null);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        String model) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, model, null, null);
    }
}
