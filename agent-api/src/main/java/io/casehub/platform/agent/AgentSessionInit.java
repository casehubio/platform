package io.casehub.platform.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
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
        ResponseFormat responseFormat,
        ModelParameters modelParameters,
        List<ToolSpecification> tools
) {
    public AgentSessionInit {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
        tools      = tools != null ? List.copyOf(tools) : List.of();
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model,
                            io.casehub.platform.api.model.ModelQuery modelQuery,
                            ModelChain modelChain,
                            ResponseFormat responseFormat,
                            ModelParameters modelParameters) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, List.of());
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model,
                            io.casehub.platform.api.model.ModelQuery modelQuery,
                            ModelChain modelChain,
                            ResponseFormat responseFormat) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, null, List.of());
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model,
                            io.casehub.platform.api.model.ModelQuery modelQuery,
                            ModelChain modelChain) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, null, null, List.of());
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model,
                            io.casehub.platform.api.model.ModelQuery modelQuery) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, null, null, null, List.of());
    }

    public AgentSessionInit(String systemPrompt, List<AgentMcpServer> mcpServers,
                            Duration timeout, String correlationId, String model) {
        this(systemPrompt, mcpServers, timeout, correlationId, model, null, null, null, null, List.of());
    }

    public static AgentSessionInit of(String systemPrompt) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, null, null, null, null, null, List.of());
    }

    public static AgentSessionInit of(String systemPrompt, String model) {
        return new AgentSessionInit(systemPrompt, List.of(), null, null, model, null, null, null, null, List.of());
    }

    public AgentSessionInit withModel(String model) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, model, null, null, responseFormat, modelParameters, tools);
    }

    public AgentSessionInit withModel(io.casehub.platform.api.model.ModelQuery modelQuery) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, null, modelQuery, null, responseFormat, modelParameters, tools);
    }

    public AgentSessionInit withModelChain(ModelChain chain) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, null, null, chain, responseFormat, modelParameters, tools);
    }

    public AgentSessionInit withResponseFormat(ResponseFormat responseFormat) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools);
    }

    public AgentSessionInit withModelParameters(ModelParameters modelParameters) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools);
    }

    public AgentSessionInit withTools(List<ToolSpecification> tools) {
        return new AgentSessionInit(systemPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools);
    }
}
