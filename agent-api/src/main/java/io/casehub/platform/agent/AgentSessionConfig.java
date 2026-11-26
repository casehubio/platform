package io.casehub.platform.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
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
        ModelChain modelChain,
        ResponseFormat responseFormat,
        ModelParameters modelParameters,
        List<ToolSpecification> tools,
        CachePolicy cachePolicy
) {
    public AgentSessionConfig {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userPrompt, "userPrompt");
        mcpServers = mcpServers != null ? List.copyOf(mcpServers) : List.of();
        tools      = tools != null ? List.copyOf(tools) : List.of();
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt,
                              List<AgentMcpServer> mcpServers, Duration timeout,
                              String correlationId, String model,
                              io.casehub.platform.api.model.ModelQuery modelQuery,
                              ModelChain modelChain,
                              ResponseFormat responseFormat,
                              ModelParameters modelParameters,
                              List<ToolSpecification> tools) {
        this(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools, null);
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt,
                              List<AgentMcpServer> mcpServers, Duration timeout,
                              String correlationId, String model,
                              io.casehub.platform.api.model.ModelQuery modelQuery,
                              ModelChain modelChain,
                              ResponseFormat responseFormat,
                              ModelParameters modelParameters) {
        this(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, List.of(), null);
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt,
                              List<AgentMcpServer> mcpServers, Duration timeout,
                              String correlationId, String model,
                              io.casehub.platform.api.model.ModelQuery modelQuery,
                              ModelChain modelChain) {
        this(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, null, null, List.of(), null);
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt,
                              List<AgentMcpServer> mcpServers, Duration timeout,
                              String correlationId, String model,
                              io.casehub.platform.api.model.ModelQuery modelQuery) {
        this(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, null, null, null, List.of(), null);
    }

    public AgentSessionConfig(String systemPrompt, String userPrompt,
                              List<AgentMcpServer> mcpServers, Duration timeout,
                              String correlationId, String model) {
        this(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, null, null, null, null, List.of(), null);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, null, null, null, null, null, List.of(), null);
    }

    public AgentSessionConfig withModel(String model) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, null, null, responseFormat, modelParameters, tools, cachePolicy);
    }

    public AgentSessionConfig withModel(io.casehub.platform.api.model.ModelQuery modelQuery) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, null, modelQuery, null, responseFormat, modelParameters, tools, cachePolicy);
    }

    public AgentSessionConfig withModelChain(ModelChain chain) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, null, null, chain, responseFormat, modelParameters, tools, cachePolicy);
    }

    public AgentSessionConfig withResponseFormat(ResponseFormat responseFormat) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools, cachePolicy);
    }

    public AgentSessionConfig withModelParameters(ModelParameters modelParameters) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools, cachePolicy);
    }

    public AgentSessionConfig withTools(List<ToolSpecification> tools) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools, cachePolicy);
    }

    public AgentSessionConfig withUserPrompt(String userPrompt) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools, cachePolicy);
    }

    public AgentSessionConfig withCachePolicy(CachePolicy cachePolicy) {
        return new AgentSessionConfig(systemPrompt, userPrompt, mcpServers, timeout, correlationId, model, modelQuery, modelChain, responseFormat, modelParameters, tools, cachePolicy);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        Duration timeout) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), timeout, null, null, null, null, null, null, List.of(), null);
    }

    public static AgentSessionConfig of(String systemPrompt, String userPrompt,
                                        String model) {
        return new AgentSessionConfig(systemPrompt, userPrompt, List.of(), null, null, model, null, null, null, null, List.of(), null);
    }
}
