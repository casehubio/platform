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
        ModelChain modelChain,
        ResponseFormat responseFormat
) {
    public AgentSessionInit {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model,
                            io.casehub.platform.api.model.ModelQuery modelQuery,
                            ModelChain modelChain) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, null);
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model,
                            io.casehub.platform.api.model.ModelQuery modelQuery) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, null, null);
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, null, null, null);
    }

    public static AgentSessionInit of(String systemPrompt) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, null, null, null, null);
    }

    public static AgentSessionInit of(String systemPrompt, String model) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, model, null, null, null);
    }

    public AgentSessionInit withModel(String model) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, model, null, null, responseFormat);
    }

    public AgentSessionInit withModel(io.casehub.platform.api.model.ModelQuery modelQuery) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, null, modelQuery, null, responseFormat);
    }

    public AgentSessionInit withModelChain(ModelChain chain) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, null, null, chain, responseFormat);
    }

    public AgentSessionInit withResponseFormat(ResponseFormat responseFormat) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat);
    }
}
