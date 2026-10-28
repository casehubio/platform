package io.casehub.platform.agent.rest;

import io.casehub.platform.agent.*;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public class AgentChatService implements AgentChatApi {

    private static final Logger LOG = Logger.getLogger(AgentChatService.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final AgentProvider agentProvider;
    private final AgentResolver agentResolver;

    public AgentChatService(AgentProvider agentProvider, AgentResolver agentResolver) {
        this.agentProvider = agentProvider;
        this.agentResolver = agentResolver;
    }

    @Override
    public List<AgentDescriptor> listAgents() {
        return agentResolver.discover();
    }

    @Override
    public AgentChatResponse invoke(AgentChatRequest request) {
        String systemPrompt;
        String model;
        Duration timeout;
        String agentId = null;
        Map<String, String> agentMetadata = Map.of();

        if (request.agentId() != null) {
            var resolved = agentResolver.resolve(request.agentId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown agent: " + request.agentId()));
            systemPrompt = resolved.systemPrompt();
            model = request.model() != null ? request.model() : resolved.model();
            timeout = request.timeoutSeconds() != null
                    ? Duration.ofSeconds(request.timeoutSeconds())
                    : resolved.timeout() != null ? resolved.timeout() : DEFAULT_TIMEOUT;
            agentId = resolved.agentId();
            agentMetadata = resolved.metadata();
        } else {
            if (request.systemPrompt() == null) {
                throw new IllegalArgumentException(
                        "systemPrompt is required when agentId is not provided");
            }
            systemPrompt = request.systemPrompt();
            model = request.model();
            timeout = request.timeoutSeconds() != null
                    ? Duration.ofSeconds(request.timeoutSeconds())
                    : DEFAULT_TIMEOUT;
        }

        var config = AgentSessionConfig.of(systemPrompt, request.userPrompt());
        if (model != null) {
            config = config.withModel(model);
        }

        LOG.debugf("Invoking agent: agentId=%s, model=%s, timeout=%s", agentId, model, timeout);

        var result = agentProvider.invokeBlocking(config, timeout);

        return new AgentChatResponse(
                result.text(),
                agentId,
                agentMetadata,
                result.backendKey(),
                result.instanceId(),
                result.inputTokens(),
                result.outputTokens(),
                result.thinkingTokens(),
                result.cacheReadTokens(),
                result.cacheWriteTokens(),
                result.totalCostUsd(),
                result.durationMs());
    }
}
