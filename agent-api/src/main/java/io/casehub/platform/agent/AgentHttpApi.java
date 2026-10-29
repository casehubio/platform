package io.casehub.platform.agent;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.model.ModelDescriptor;
import io.casehub.platform.api.model.ModelQuery;
import io.smallrye.mutiny.Multi;

import java.util.List;

@McpDomain("agent-http")
public interface AgentHttpApi {

    @PlatformMutation("Invoke an agent (blocking)")
    AgentInvocationResult invoke(AgentSessionConfig config, String agentId);

    @PlatformMutation(value = "Invoke an agent (streaming)", streaming = true)
    Multi<AgentEvent> invokeStream(AgentSessionConfig config, String agentId);

    @PlatformMutation("Open a managed session")
    SessionHandle openSession(AgentSessionInit init, String agentId);

    @PlatformMutation(value = "Query within a session", streaming = true)
    Multi<AgentEvent> querySession(String sessionId, String prompt);

    @PlatformMutation("Interrupt a session")
    void interruptSession(String sessionId);

    @PlatformMutation("Clear session context")
    void clearSession(String sessionId);

    @PlatformMutation("Close a session")
    void closeSession(String sessionId);

    @PlatformQuery("List available agents")
    List<AgentDescriptor> listAgents();

    @PlatformQuery("List registered backends")
    List<BackendInfo> listBackends();

    @PlatformQuery("List available models")
    List<ModelDescriptor> listModels(ModelQuery query);

    @PlatformQuery("List active sessions")
    List<SessionInfo> listSessions();

    @PlatformQuery("Module status and health")
    AgentHttpStatus status();
}
