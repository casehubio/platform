package io.casehub.platform.agent;

import io.casehub.platform.api.model.ModelChain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record AgentSessionInit(
        String systemPrompt,
        List<AgentMcpServer> mcpServers,
        Duration timeout,
        String correlationId,
        String model,
        io.casehub.platform.api.model.ModelQuery modelQuery,
        ModelChain modelChain
) {
    public AgentSessionInit {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model,
                            io.casehub.platform.api.model.ModelQuery modelQuery) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, null);
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, null, null);
    }

    public static AgentSessionInit of(String systemPrompt) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, null, null, null);
    }

    public static AgentSessionInit of(String systemPrompt, String model) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, model, null, null);
    }

    public AgentSessionInit withModel(String model) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, model, null, null);
    }

    public AgentSessionInit withModel(io.casehub.platform.api.model.ModelQuery modelQuery) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, null, modelQuery, null);
    }

    public AgentSessionInit withModelChain(ModelChain chain) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, null, null, chain);
    }
}
