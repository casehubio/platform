package io.casehub.platform.agent.http;

import io.casehub.platform.agent.AgentDescriptor;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentHttpApi;
import io.casehub.platform.agent.AgentHttpStatus;
import io.casehub.platform.agent.AgentInvocationResult;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentResolver;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.BackendInfo;
import io.casehub.platform.agent.BackendInstanceRegistry;
import io.casehub.platform.agent.SessionHandle;
import io.casehub.platform.agent.SessionInfo;
import io.casehub.platform.api.mcp.ComponentStatus;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelQuery;
import io.casehub.platform.api.model.ModelRegistry;
import io.smallrye.mutiny.Multi;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class AgentHttpService implements AgentHttpApi {

    private static final Logger LOG = Logger.getLogger(AgentHttpService.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

    private final AgentProvider agentProvider;
    private final AgentResolver agentResolver;
    private final HttpSessionManager sessionManager;
    private final BackendInstanceRegistry backendRegistry;
    private final ModelRegistry modelRegistry;

    public AgentHttpService(AgentProvider agentProvider,
                            AgentResolver agentResolver,
                            HttpSessionManager sessionManager,
                            BackendInstanceRegistry backendRegistry,
                            ModelRegistry modelRegistry) {
        this.agentProvider = Objects.requireNonNull(agentProvider);
        this.agentResolver = agentResolver;
        this.sessionManager = sessionManager;
        this.backendRegistry = backendRegistry;
        this.modelRegistry = modelRegistry;
    }

    @Override
    public AgentInvocationResult invoke(AgentSessionConfig config, String agentId) {
        var resolved = resolveConfig(config, agentId);
        Duration timeout = resolved.timeout() != null ? resolved.timeout() : DEFAULT_TIMEOUT;
        LOG.debugf("Invoking agent: agentId=%s, model=%s, timeout=%s", agentId, resolved.model(), timeout);
        return agentProvider.invokeBlocking(resolved, timeout);
    }

    @Override
    public Multi<AgentEvent> invokeStream(AgentSessionConfig config, String agentId) {
        var resolved = resolveConfig(config, agentId);
        LOG.debugf("Streaming invoke: agentId=%s, model=%s", agentId, resolved.model());
        return agentProvider.invoke(resolved);
    }

    @Override
    public SessionHandle openSession(AgentSessionInit init, String agentId) {
        requireSessions();
        var resolvedInit = resolveInit(init, agentId);
        LOG.debugf("Opening session: agentId=%s, model=%s", agentId, resolvedInit.model());
        return sessionManager.open(resolvedInit);
    }

    @Override
    public Multi<AgentEvent> querySession(String sessionId, String prompt) {
        requireSessions();
        var session = sessionManager.get(sessionId);
        return session.query(prompt);
    }

    @Override
    public void interruptSession(String sessionId) {
        requireSessions();
        var session = sessionManager.get(sessionId);
        session.interrupt();
    }

    @Override
    public void clearSession(String sessionId) {
        requireSessions();
        var session = sessionManager.get(sessionId);
        session.clear();
    }

    @Override
    public void closeSession(String sessionId) {
        requireSessions();
        var session = sessionManager.get(sessionId);
        try {
            session.close(DEFAULT_TIMEOUT);
        } finally {
            sessionManager.release(sessionId);
        }
    }

    @Override
    public List<AgentDescriptor> listAgents() {
        return agentResolver != null ? agentResolver.discover() : List.of();
    }

    @Override
    public List<BackendInfo> listBackends() {
        if (backendRegistry == null) return List.of();
        return backendRegistry.keys().stream()
                .sorted()
                .map(key -> new BackendInfo(key, backendRegistry.resolveByKey(key).size()))
                .toList();
    }

    @Override
    public List<ModelDescriptor> listModels(ModelQuery query) {
        if (modelRegistry == null) return List.of();
        return query != null ? modelRegistry.query(query) : modelRegistry.all();
    }

    @Override
    public List<SessionInfo> listSessions() {
        return sessionManager != null ? sessionManager.listSessions() : List.of();
    }

    @Override
    public AgentHttpStatus status() {
        Set<String> capabilities = new LinkedHashSet<>();
        capabilities.add("invoke");
        if (sessionManager != null) capabilities.add("sessions");
        if (backendRegistry != null) capabilities.add("backends");
        if (modelRegistry != null) capabilities.add("models");
        int activeSessions = sessionManager != null ? sessionManager.activeCount() : 0;
        Map<String, Object> pools = new LinkedHashMap<>();
        if (backendRegistry != null) pools.put("backendCount", backendRegistry.keys().size());
        if (modelRegistry != null) pools.put("modelCount", modelRegistry.all().size());
        return new AgentHttpStatus(capabilities, pools, activeSessions, ComponentStatus.AVAILABLE);
    }

    private void requireSessions() {
        if (sessionManager == null) {
            throw new UnsupportedOperationException("Multi-turn sessions not available — SessionRegistry not on classpath");
        }
    }

    private AgentSessionConfig resolveConfig(AgentSessionConfig config, String agentId) {
        if (agentId == null || agentId.isEmpty()) return config;
        if (agentResolver == null) {
            throw new IllegalArgumentException("Agent resolution not available — AgentResolver not on classpath");
        }
        var agent = agentResolver.resolve(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent: " + agentId));
        return new AgentSessionConfig(
                agent.systemPrompt(),
                config.userPrompt(),
                config.mcpServers(),
                agent.timeout() != null ? agent.timeout() : config.timeout(),
                config.correlationId(),
                config.model() != null ? config.model() : agent.model(),
                config.modelQuery(),
                config.modelChain(),
                config.responseFormat());
    }

    private AgentSessionInit resolveInit(AgentSessionInit init, String agentId) {
        if (agentId == null || agentId.isEmpty()) return init;
        if (agentResolver == null) {
            throw new IllegalArgumentException("Agent resolution not available — AgentResolver not on classpath");
        }
        var agent = agentResolver.resolve(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown agent: " + agentId));
        return new AgentSessionInit(
                agent.systemPrompt(),
                init.mcpServers(),
                agent.timeout() != null ? agent.timeout() : init.timeout(),
                init.correlationId(),
                init.model() != null ? init.model() : agent.model(),
                init.modelQuery(),
                init.modelChain(),
                init.responseFormat());
    }
}
